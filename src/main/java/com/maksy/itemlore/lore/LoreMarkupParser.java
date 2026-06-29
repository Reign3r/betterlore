package com.maksy.itemlore.lore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LoreMarkupParser {
	public static final int MAX_VISIBLE_CODEPOINTS = 255;
	public static final int MAX_RAW_CHARS = 4096;
	public static final int MAX_COLOR_TAGS = 64;
	public static final int MAX_LINES = 16;
	public static final int DEFAULT_COLOR = 0xAAAAAA;
	public static final int WRAP_VISIBLE_CHARS = 42;

	private static final String COLOR_OPEN_PREFIX = "[color:#";
	private static final String COLOR_CLOSE = "[/color]";
	private static final String GRADIENT_OPEN_PREFIX = "[gradient:#";
	private static final String GRADIENT_CLOSE_PREFIX = "[/gradient:#";

	private LoreMarkupParser() {
	}

	public static ParseResult parse(String raw) {
		if (raw == null || raw.isEmpty()) {
			return ParseResult.success(LoreDocument.empty());
		}

		if (raw.length() > MAX_RAW_CHARS) {
			return ParseResult.error("Lore input is too long.", 0);
		}

		ParserState state = new ParserState(raw.replace("\r\n", "\n").replace('\r', '\n'));
		ParseResult error = state.parse();
		if (error != null) {
			return error;
		}

		return ParseResult.success(state.buildDocument());
	}

	public static String formatHex(int rgb) {
		return String.format(Locale.ROOT, "#%06x", rgb & 0xFFFFFF);
	}

	private static final class ParserState {
		private final String input;
		private final List<LoreLine> lines = new ArrayList<>();
		private final List<LoreRun> currentRuns = new ArrayList<>();
		private final List<Integer> colorStack = new ArrayList<>();
		private final StringBuilder currentText = new StringBuilder();
		private int visible;
		private int tagCount;

		private ParserState(String input) {
			this.input = input;
			this.colorStack.add(DEFAULT_COLOR);
		}

		private ParseResult parse() {
			for (int i = 0; i < input.length();) {
				ColorTag colorTag = readColorOpen(input, i);
				if (colorTag != null) {
					flushRun(currentColor());
					colorStack.add(colorTag.rgb());
					ParseResult error = countTag();
					if (error != null) {
						return error;
					}
					i = colorTag.endIndex();
					continue;
				}

				if (startsWithIgnoreCase(input, i, COLOR_CLOSE)) {
					flushRun(currentColor());
					if (colorStack.size() > 1) {
						colorStack.remove(colorStack.size() - 1);
					}
					ParseResult error = countTag();
					if (error != null) {
						return error;
					}
					i += COLOR_CLOSE.length();
					continue;
				}

				ColorTag gradientOpen = readGradientOpen(input, i);
				if (gradientOpen != null) {
					GradientClose gradientClose = findGradientClose(input, gradientOpen.endIndex());
					if (gradientClose != null) {
						flushRun(currentColor());
						ParseResult error = countTag();
						if (error != null) {
							return error;
						}
						error = countTag();
						if (error != null) {
							return error;
						}

						String gradientText = input.substring(gradientOpen.endIndex(), gradientClose.startIndex());
						error = appendGradient(gradientText, gradientOpen.rgb(), gradientClose.rgb());
						if (error != null) {
							return error;
						}
						i = gradientClose.endIndex();
						continue;
					}
				}

				AppendResult result = appendLiteralAt(input, i, currentColor());
				if (result.error() != null) {
					return result.error();
				}
				i = result.nextIndex();
			}

			flushRun(currentColor());
			lines.add(new LoreLine(List.copyOf(currentRuns)));
			return null;
		}

		private ParseResult appendGradient(String text, int startColor, int endColor) {
			List<Integer> codePoints = new ArrayList<>();
			int gradientVisible = 0;

			for (int i = 0; i < text.length();) {
				GradientLiteral literal = readGradientLiteralAt(text, i);
				if (literal.error() != null) {
					return ParseResult.error(literal.error(), visible + gradientVisible);
				}

				for (int cp : literal.codePoints()) {
					codePoints.add(cp);
					if (cp != '\n') {
						gradientVisible++;
					}
				}
				i = literal.nextIndex();
			}

			if (visible + gradientVisible > MAX_VISIBLE_CODEPOINTS) {
				return ParseResult.error("Lore is limited to 255 visible symbols.", visible + gradientVisible);
			}

			int visibleInGradient = Math.max(1, gradientVisible);
			int gradientIndex = 0;
			for (int cp : codePoints) {
				if (cp == '\n') {
					ParseResult error = addLineBreak();
					if (error != null) {
						return error;
					}
					continue;
				}

				appendCodePoint(cp, interpolateColor(startColor, endColor, gradientIndex, visibleInGradient));
				visible++;
				gradientIndex++;
			}

			return null;
		}

		private AppendResult appendLiteralAt(String source, int index, int color) {
			if (source.startsWith("\\[", index)) {
				return appendVisibleCodePoint('[', index + 2, color);
			}

			if (source.startsWith("\\\\", index)) {
				return appendVisibleCodePoint('\\', index + 2, color);
			}

			char ch = source.charAt(index);
			if (Character.isHighSurrogate(ch)) {
				if (index + 1 >= source.length() || !Character.isLowSurrogate(source.charAt(index + 1))) {
					return new AppendResult(index + 1, ParseResult.error("Invalid Unicode input.", visible));
				}
			} else if (Character.isLowSurrogate(ch)) {
				return new AppendResult(index + 1, ParseResult.error("Invalid Unicode input.", visible));
			}

			int cp = source.codePointAt(index);
			int nextIndex = index + Character.charCount(cp);
			if (cp == '\n') {
				return new AppendResult(nextIndex, addLineBreak());
			}

			if (cp == '\t') {
				for (int i = 0; i < 4; i++) {
					ParseResult error = appendVisibleCodePoint(' ', nextIndex, color).error();
					if (error != null) {
						return new AppendResult(nextIndex, error);
					}
				}
				return new AppendResult(nextIndex, null);
			}

			if (cp == '\u00A7') {
				return new AppendResult(nextIndex, ParseResult.error("Legacy formatting codes are not allowed.", visible));
			}

			if (Character.isISOControl(cp)) {
				return new AppendResult(nextIndex, ParseResult.error("Control characters are not allowed.", visible));
			}

			return appendVisibleCodePoint(cp, nextIndex, color);
		}

		private AppendResult appendVisibleCodePoint(int cp, int nextIndex, int color) {
			appendCodePoint(cp, color);
			visible++;
			if (visible > MAX_VISIBLE_CODEPOINTS) {
				return new AppendResult(nextIndex, ParseResult.error("Lore is limited to 255 visible symbols.", visible));
			}
			return new AppendResult(nextIndex, null);
		}

		private void appendCodePoint(int cp, int color) {
			currentText.appendCodePoint(cp);
			flushRun(color);
		}

		private void flushRun(int color) {
			if (currentText.isEmpty()) {
				return;
			}

			String value = currentText.toString();
			if (!currentRuns.isEmpty()) {
				LoreRun previous = currentRuns.getLast();
				if (previous.rgb() == color) {
					currentRuns.set(currentRuns.size() - 1, new LoreRun(previous.text() + value, color));
					currentText.setLength(0);
					return;
				}
			}

			currentRuns.add(new LoreRun(value, color));
			currentText.setLength(0);
		}

		private ParseResult addLineBreak() {
			flushRun(currentColor());
			lines.add(new LoreLine(List.copyOf(currentRuns)));
			currentRuns.clear();

			if (lines.size() >= MAX_LINES) {
				return ParseResult.error("Too many lore lines.", visible);
			}

			return null;
		}

		private ParseResult countTag() {
			tagCount++;
			if (tagCount > MAX_COLOR_TAGS) {
				return ParseResult.error("Too many color or gradient tags.", visible);
			}
			return null;
		}

		private int currentColor() {
			return colorStack.getLast();
		}

		private LoreDocument buildDocument() {
			return new LoreDocument(trimTrailingEmptyLines(lines), visible);
		}
	}

	private static GradientLiteral readGradientLiteralAt(String source, int index) {
		if (source.startsWith("\\[", index)) {
			return new GradientLiteral(List.of((int) '['), index + 2, null);
		}

		if (source.startsWith("\\\\", index)) {
			return new GradientLiteral(List.of((int) '\\'), index + 2, null);
		}

		char ch = source.charAt(index);
		if (Character.isHighSurrogate(ch)) {
			if (index + 1 >= source.length() || !Character.isLowSurrogate(source.charAt(index + 1))) {
				return new GradientLiteral(List.of(), index + 1, "Invalid Unicode input.");
			}
		} else if (Character.isLowSurrogate(ch)) {
			return new GradientLiteral(List.of(), index + 1, "Invalid Unicode input.");
		}

		int cp = source.codePointAt(index);
		int nextIndex = index + Character.charCount(cp);
		if (cp == '\n') {
			return new GradientLiteral(List.of((int) '\n'), nextIndex, null);
		}

		if (cp == '\t') {
			return new GradientLiteral(List.of((int) ' ', (int) ' ', (int) ' ', (int) ' '), nextIndex, null);
		}

		if (cp == '\u00A7') {
			return new GradientLiteral(List.of(), nextIndex, "Legacy formatting codes are not allowed.");
		}

		if (Character.isISOControl(cp)) {
			return new GradientLiteral(List.of(), nextIndex, "Control characters are not allowed.");
		}

		return new GradientLiteral(List.of(cp), nextIndex, null);
	}

	private static int interpolateColor(int startColor, int endColor, int index, int count) {
		if (count <= 1) {
			return startColor & 0xFFFFFF;
		}

		double t = (double) index / (double) (count - 1);
		int sr = (startColor >> 16) & 0xFF;
		int sg = (startColor >> 8) & 0xFF;
		int sb = startColor & 0xFF;
		int er = (endColor >> 16) & 0xFF;
		int eg = (endColor >> 8) & 0xFF;
		int eb = endColor & 0xFF;

		int r = (int) Math.round(sr + (er - sr) * t);
		int g = (int) Math.round(sg + (eg - sg) * t);
		int b = (int) Math.round(sb + (eb - sb) * t);
		return (r << 16) | (g << 8) | b;
	}

	private static ColorTag readColorOpen(String s, int index) {
		return readTagWithColor(s, index, COLOR_OPEN_PREFIX);
	}

	private static ColorTag readGradientOpen(String s, int index) {
		return readTagWithColor(s, index, GRADIENT_OPEN_PREFIX);
	}

	private static ColorTag readTagWithColor(String s, int index, String prefix) {
		int length = prefix.length() + 6 + 1;
		if (index + length > s.length() || !startsWithIgnoreCase(s, index, prefix) || s.charAt(index + length - 1) != ']') {
			return null;
		}

		int colorStart = index + prefix.length();
		if (!hasSixHexDigits(s, colorStart)) {
			return null;
		}

		return new ColorTag(parseHexColor(s, colorStart), index + length);
	}

	private static GradientClose findGradientClose(String s, int startIndex) {
		for (int i = startIndex; i < s.length(); i++) {
			if (!startsWithIgnoreCase(s, i, GRADIENT_CLOSE_PREFIX)) {
				continue;
			}

			int end = i + GRADIENT_CLOSE_PREFIX.length() + 6 + 1;
			if (end > s.length() || s.charAt(end - 1) != ']') {
				continue;
			}

			int colorStart = i + GRADIENT_CLOSE_PREFIX.length();
			if (!hasSixHexDigits(s, colorStart)) {
				continue;
			}

			return new GradientClose(i, end, parseHexColor(s, colorStart));
		}

		return null;
	}

	private static boolean hasSixHexDigits(String s, int start) {
		if (start + 6 > s.length()) {
			return false;
		}

		for (int i = start; i < start + 6; i++) {
			if (Character.digit(s.charAt(i), 16) < 0) {
				return false;
			}
		}

		return true;
	}

	private static int parseHexColor(String s, int start) {
		return Integer.parseInt(s.substring(start, start + 6).toLowerCase(Locale.ROOT), 16);
	}

	private static boolean startsWithIgnoreCase(String s, int index, String prefix) {
		return index >= 0 && index + prefix.length() <= s.length() && s.regionMatches(true, index, prefix, 0, prefix.length());
	}

	private static List<LoreLine> trimTrailingEmptyLines(List<LoreLine> lines) {
		int end = lines.size();
		while (end > 0 && lines.get(end - 1).isEmpty()) {
			end--;
		}

		return List.copyOf(lines.subList(0, end));
	}

	private record AppendResult(int nextIndex, ParseResult error) {
	}

	private record ColorTag(int rgb, int endIndex) {
	}

	private record GradientClose(int startIndex, int endIndex, int rgb) {
	}

	private record GradientLiteral(List<Integer> codePoints, int nextIndex, String error) {
	}
}
