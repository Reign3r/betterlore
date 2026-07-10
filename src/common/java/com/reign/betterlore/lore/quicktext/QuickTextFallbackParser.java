package com.reign.betterlore.lore.quicktext;

import com.reign.betterlore.lore.LoreDocument;
import com.reign.betterlore.lore.LoreLine;
import com.reign.betterlore.lore.LoreMarkupParser;
import com.reign.betterlore.lore.LoreRun;
import com.reign.betterlore.lore.ParseResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class QuickTextFallbackParser {
	private QuickTextFallbackParser() {
	}

	static ParseResult parse(String input) {
		Parser parser = new Parser(input);
		ParseResult error = parser.parseUntil(null);
		if (error != null) {
			return error;
		}
		return ParseResult.success(parser.buildDocument());
	}

	private static final class Parser {
		private final String input;
		private final List<LoreLine> lines = new ArrayList<>();
		private final List<LoreRun> currentRuns = new ArrayList<>();
		private final List<Integer> colorStack = new ArrayList<>();
		private final StringBuilder currentText = new StringBuilder();
		private int index;
		private int visible;
		private int boldDepth;
		private int italicDepth;
		private int underlinedDepth;
		private int strikethroughDepth;
		private int obfuscatedDepth;

		private Parser(String input) {
			this.input = input;
			this.colorStack.add(LoreMarkupParser.DEFAULT_COLOR);
		}

		private ParseResult parseUntil(String closingTag) {
			while (index < input.length()) {
				Tag tag = readTagAt(input, index);
				if (tag != null) {
					if (tag.closing()) {
						if (closingTag != null && canonicalTagName(tag.name()).equals(canonicalTagName(closingTag))) {
							index = tag.endIndex();
							return null;
						}
						return appendLiteralTag(tag);
					}

					ParseResult result = handleOpeningTag(tag);
					if (result != null) {
						return result;
					}
					continue;
				}

				ParseResult result = appendLiteralAt(index);
				if (result != null) {
					return result;
				}
			}
			return null;
		}

		private ParseResult handleOpeningTag(Tag tag) {
			return switch (tag.name()) {
				case "c", "color" -> handleColorTag(tag);
				case "gr", "gradient", "hgr", "hard_gradient" -> handleGradientTag(tag);
				case "b", "bold" -> handleFormatTag(tag, LoreRun.BOLD);
				case "i", "italic" -> handleFormatTag(tag, LoreRun.ITALIC);
				case "u", "underline", "underlined" -> handleFormatTag(tag, LoreRun.UNDERLINED);
				case "s", "st", "strikethrough" -> handleFormatTag(tag, LoreRun.STRIKETHROUGH);
				case "o", "obf", "obfuscated" -> handleFormatTag(tag, LoreRun.OBFUSCATED);
				default -> appendLiteralTag(tag);
			};
		}

		private ParseResult handleColorTag(Tag tag) {
			if (tag.args().size() != 1) {
				return appendLiteralTag(tag);
			}

			Integer rgb = parseHex(tag.args().getFirst());
			if (rgb == null) {
				return appendLiteralTag(tag);
			}

			flushRun(currentColor(), currentFlags());
			colorStack.add(rgb);
			index = tag.endIndex();
			ParseResult result = parseUntil(tag.name());
			flushRun(currentColor(), currentFlags());
			if (colorStack.size() > 1) {
				colorStack.remove(colorStack.size() - 1);
			}
			return result;
		}

		private ParseResult handleGradientTag(Tag tag) {
			GradientArguments arguments = parseGradientArguments(tag);
			if (arguments == null) {
				return appendLiteralTag(tag);
			}

			int contentStart = tag.endIndex();
			int closeStart = findClosingTag(input, contentStart, gradientCloseName(tag.name()));
			if (closeStart < 0) {
				return appendLiteralTag(tag);
			}

			String text = input.substring(contentStart, closeStart);
			Tag closeTag = readTagAt(input, closeStart);
			index = closeTag == null ? closeStart : closeTag.endIndex();
			flushRun(currentColor(), currentFlags());
			return appendGradientText(text, arguments.colors(), arguments.hard(), currentFlags());
		}

		private ParseResult handleFormatTag(Tag tag, int flag) {
			flushRun(currentColor(), currentFlags());
			addFlag(flag, 1);
			index = tag.endIndex();
			ParseResult result = parseUntil(tag.name());
			flushRun(currentColor(), currentFlags());
			addFlag(flag, -1);
			return result;
		}

		private ParseResult appendGradientText(String text, List<Integer> colors, boolean hard, int flags) {
			List<Integer> codePoints = new ArrayList<>();
			for (int i = 0; i < text.length();) {
				if (text.startsWith("\\<", i)) {
					codePoints.add((int) '<');
					i += 2;
					continue;
				}
				if (text.startsWith("\\\\", i)) {
					codePoints.add((int) '\\');
					i += 2;
					continue;
				}
				int cp = text.codePointAt(i);
				codePoints.add(cp);
				i += Character.charCount(cp);
			}

			int visibleInGradient = 0;
			for (int cp : codePoints) {
				if (cp != '\n') {
					visibleInGradient++;
				}
			}

			int gradientIndex = 0;
			for (int cp : codePoints) {
				if (cp == '\n') {
					ParseResult error = addLineBreak();
					if (error != null) {
						return error;
					}
					continue;
				}

				ParseResult error = appendVisibleCodePoint(cp, interpolate(colors, gradientIndex, Math.max(1, visibleInGradient), hard), flags);
				if (error != null) {
					return error;
				}
				gradientIndex++;
			}
			return null;
		}

		private ParseResult appendLiteralTag(Tag tag) {
			String literal = input.substring(index, tag.endIndex());
			index = tag.endIndex();
			for (int i = 0; i < literal.length();) {
				int cp = literal.codePointAt(i);
				ParseResult error = appendVisibleCodePoint(cp, currentColor(), currentFlags());
				if (error != null) {
					return error;
				}
				i += Character.charCount(cp);
			}
			return null;
		}

		private ParseResult appendLiteralAt(int sourceIndex) {
			if (input.startsWith("\\<", sourceIndex)) {
				index = sourceIndex + 2;
				return appendVisibleCodePoint('<', currentColor(), currentFlags());
			}
			if (input.startsWith("\\\\", sourceIndex)) {
				index = sourceIndex + 2;
				return appendVisibleCodePoint('\\', currentColor(), currentFlags());
			}

			int cp = input.codePointAt(sourceIndex);
			index = sourceIndex + Character.charCount(cp);
			if (cp == '\n') {
				return addLineBreak();
			}
			return appendVisibleCodePoint(cp, currentColor(), currentFlags());
		}

		private ParseResult appendVisibleCodePoint(int cp, int color, int flags) {
			currentText.appendCodePoint(cp);
			flushRun(color, flags);
			visible++;
			if (visible > LoreMarkupParser.MAX_VISIBLE_CODEPOINTS) {
				return ParseResult.error("Lore is limited to 255 visible symbols.", visible);
			}
			return null;
		}

		private void flushRun(int color, int flags) {
			if (currentText.isEmpty()) {
				return;
			}

			String value = currentText.toString();
			if (!currentRuns.isEmpty()) {
				LoreRun previous = currentRuns.getLast();
				if (previous.rgb() == color && previous.flags() == flags) {
					currentRuns.set(currentRuns.size() - 1, new LoreRun(previous.text() + value, color, flags));
					currentText.setLength(0);
					return;
				}
			}

			currentRuns.add(new LoreRun(value, color, flags));
			currentText.setLength(0);
		}

		private ParseResult addLineBreak() {
			flushRun(currentColor(), currentFlags());
			lines.add(new LoreLine(List.copyOf(currentRuns)));
			currentRuns.clear();

			if (lines.size() >= LoreMarkupParser.MAX_LINES) {
				return ParseResult.error("Too many lore lines.", visible);
			}
			return null;
		}

		private LoreDocument buildDocument() {
			flushRun(currentColor(), currentFlags());
			lines.add(new LoreLine(List.copyOf(currentRuns)));
			return new LoreDocument(trimTrailingEmptyLines(lines), visible);
		}

		private int currentColor() {
			return colorStack.getLast();
		}

		private int currentFlags() {
			int flags = 0;
			if (boldDepth > 0) {
				flags |= LoreRun.BOLD;
			}
			if (italicDepth > 0) {
				flags |= LoreRun.ITALIC;
			}
			if (underlinedDepth > 0) {
				flags |= LoreRun.UNDERLINED;
			}
			if (strikethroughDepth > 0) {
				flags |= LoreRun.STRIKETHROUGH;
			}
			if (obfuscatedDepth > 0) {
				flags |= LoreRun.OBFUSCATED;
			}
			return flags;
		}

		private void addFlag(int flag, int delta) {
			switch (flag) {
				case LoreRun.BOLD -> boldDepth = Math.max(0, boldDepth + delta);
				case LoreRun.ITALIC -> italicDepth = Math.max(0, italicDepth + delta);
				case LoreRun.UNDERLINED -> underlinedDepth = Math.max(0, underlinedDepth + delta);
				case LoreRun.STRIKETHROUGH -> strikethroughDepth = Math.max(0, strikethroughDepth + delta);
				case LoreRun.OBFUSCATED -> obfuscatedDepth = Math.max(0, obfuscatedDepth + delta);
				default -> {
				}
			}
		}
	}

	private static Tag readTagAt(String input, int index) {
		if (index < 0 || index >= input.length() || input.charAt(index) != '<' || isEscaped(input, index)) {
			return null;
		}

		int end = findTagEnd(input, index + 1);
		if (end < 0) {
			return null;
		}

		String body = input.substring(index + 1, end).trim();
		if (body.isEmpty()) {
			return null;
		}

		boolean closing = body.startsWith("/");
		if (closing) {
			body = body.substring(1).trim();
		}

		List<String> parts = splitTagBody(body);
		if (parts.isEmpty()) {
			return null;
		}

		return new Tag(parts.getFirst().toLowerCase(Locale.ROOT), List.copyOf(parts.subList(1, parts.size())), closing, end + 1);
	}

	private static int findClosingTag(String input, int start, String canonicalName) {
		for (int i = start; i < input.length(); i++) {
			Tag tag = readTagAt(input, i);
			if (tag != null && tag.closing() && canonicalTagName(tag.name()).equals(canonicalName)) {
				return i;
			}
		}
		return -1;
	}

	private static int findTagEnd(String input, int start) {
		for (int i = start; i < input.length(); i++) {
			if (input.charAt(i) == '>') {
				return i;
			}
		}
		return -1;
	}

	private static List<String> splitTagBody(String body) {
		String[] rawParts = body.split("\\s+");
		List<String> parts = new ArrayList<>(rawParts.length);
		for (String part : rawParts) {
			if (!part.isEmpty()) {
				parts.add(part);
			}
		}
		return parts;
	}

	private static Integer parseHex(String value) {
		String hex = value.startsWith("#") ? value.substring(1) : value;
		if (hex.length() != 6) {
			return null;
		}
		for (int i = 0; i < hex.length(); i++) {
			if (Character.digit(hex.charAt(i), 16) < 0) {
				return null;
			}
		}
		return Integer.parseInt(hex, 16);
	}

	private static GradientArguments parseGradientArguments(Tag tag) {
		List<Integer> colors = new ArrayList<>();
		boolean hard = tag.name().equals("hgr") || tag.name().equals("hard_gradient");
		for (String arg : tag.args()) {
			String lower = arg.toLowerCase(Locale.ROOT);
			if (lower.equals("hard") || lower.equals("type:hard")) {
				hard = true;
				continue;
			}
			if (lower.equals("oklab") || lower.equals("type:oklab") || lower.equals("hsv") || lower.equals("type:hsv") || lower.equals("hvs") || lower.equals("type:hvs")) {
				// QuickText accepts interpolation modifiers. Better Lore's clean-room fallback
				// keeps oklab as the supported smooth mode and ignores unknown smooth aliases
				// rather than failing user input that works on Fabric's library-backed parser.
				continue;
			}

			Integer rgb = parseHex(arg);
			if (rgb == null) {
				return null;
			}
			colors.add(rgb);
		}
		return colors.size() < 2 ? null : new GradientArguments(List.copyOf(colors), hard);
	}

	private static String gradientCloseName(String tagName) {
		return switch (tagName) {
			case "hgr", "hard_gradient" -> "gradient";
			default -> canonicalTagName(tagName);
		};
	}

	private static String canonicalTagName(String tagName) {
		return switch (tagName) {
			case "color" -> "c";
			case "gradient", "hgr", "hard_gradient" -> "gradient";
			case "bold" -> "b";
			case "italic" -> "i";
			case "underline", "underlined" -> "u";
			case "st", "strikethrough" -> "s";
			case "obf", "obfuscated" -> "o";
			default -> tagName;
		};
	}

	private static int interpolate(List<Integer> colors, int index, int count, boolean hard) {
		if (colors.size() == 1 || count <= 1) {
			return colors.getFirst();
		}

		double scaled = (double) index * (colors.size() - 1) / (double) (count - 1);
		int segment = Math.min(colors.size() - 2, (int) Math.floor(scaled));
		double t = scaled - segment;
		if (hard) {
			return colors.get(Math.min(colors.size() - 1, (int) Math.round(scaled)));
		}

		return interpolateOklab(colors.get(segment), colors.get(segment + 1), t);
	}

	private static int interpolateOklab(int start, int end, double t) {
		double[] a = rgbToOklab(start);
		double[] b = rgbToOklab(end);
		double l = lerp(a[0], b[0], t);
		double aa = lerp(a[1], b[1], t);
		double bb = lerp(a[2], b[2], t);
		return oklabToRgb(l, aa, bb);
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	private static double[] rgbToOklab(int rgb) {
		double r = srgbToLinear((rgb >> 16) & 0xFF);
		double g = srgbToLinear((rgb >> 8) & 0xFF);
		double b = srgbToLinear(rgb & 0xFF);

		double l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b;
		double m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b;
		double s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b;

		double lRoot = Math.cbrt(l);
		double mRoot = Math.cbrt(m);
		double sRoot = Math.cbrt(s);

		return new double[] {
				0.2104542553 * lRoot + 0.7936177850 * mRoot - 0.0040720468 * sRoot,
				1.9779984951 * lRoot - 2.4285922050 * mRoot + 0.4505937099 * sRoot,
				0.0259040371 * lRoot + 0.7827717662 * mRoot - 0.8086757660 * sRoot
		};
	}

	private static int oklabToRgb(double l, double a, double b) {
		double lPrime = l + 0.3963377774 * a + 0.2158037573 * b;
		double mPrime = l - 0.1055613458 * a - 0.0638541728 * b;
		double sPrime = l - 0.0894841775 * a - 1.2914855480 * b;

		double l3 = lPrime * lPrime * lPrime;
		double m3 = mPrime * mPrime * mPrime;
		double s3 = sPrime * sPrime * sPrime;

		double r = 4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3;
		double g = -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3;
		double blue = -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3;

		return (linearToSrgb(r) << 16) | (linearToSrgb(g) << 8) | linearToSrgb(blue);
	}

	private static double srgbToLinear(int channel) {
		double value = channel / 255.0;
		return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
	}

	private static int linearToSrgb(double value) {
		value = Math.max(0.0, Math.min(1.0, value));
		double srgb = value <= 0.0031308 ? 12.92 * value : 1.055 * Math.pow(value, 1.0 / 2.4) - 0.055;
		return Math.max(0, Math.min(255, (int) Math.round(srgb * 255.0)));
	}


	private static boolean isEscaped(String input, int index) {
		int backslashes = 0;
		for (int i = index - 1; i >= 0 && input.charAt(i) == '\\'; i--) {
			backslashes++;
		}
		return (backslashes & 1) == 1;
	}

	private static List<LoreLine> trimTrailingEmptyLines(List<LoreLine> lines) {
		int end = lines.size();
		while (end > 0 && lines.get(end - 1).isEmpty()) {
			end--;
		}
		return List.copyOf(lines.subList(0, end));
	}

	private record Tag(String name, List<String> args, boolean closing, int endIndex) {
	}

	private record GradientArguments(List<Integer> colors, boolean hard) {
	}
}
