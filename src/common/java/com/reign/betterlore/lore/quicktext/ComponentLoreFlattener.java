package com.reign.betterlore.lore.quicktext;

import com.reign.betterlore.lore.LoreDocument;
import com.reign.betterlore.lore.LoreLine;
import com.reign.betterlore.lore.LoreMarkupParser;
import com.reign.betterlore.lore.LoreRun;
import com.reign.betterlore.lore.ParseResult;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class ComponentLoreFlattener {
	private ComponentLoreFlattener() {
	}

	public static ParseResult flatten(Component component) {
		if (component == null) {
			return ParseResult.success(LoreDocument.empty());
		}

		Builder builder = new Builder();
		AtomicReference<ParseResult> error = new AtomicReference<>();
		component.visit((style, text) -> {
			if (error.get() == null) {
				ParseResult result = builder.append(text, style);
				if (result != null) {
					error.set(result);
				}
			}
			return Optional.empty();
		}, Style.EMPTY);

		if (error.get() != null) {
			return error.get();
		}

		return ParseResult.success(builder.buildDocument());
	}

	private static final class Builder {
		private final List<LoreLine> lines = new ArrayList<>();
		private final List<LoreRun> currentRuns = new ArrayList<>();
		private final StringBuilder currentText = new StringBuilder();
		private int visible;
		private int currentColor = LoreMarkupParser.DEFAULT_COLOR;
		private int currentFlags;

		private ParseResult append(String text, Style style) {
			if (text == null || text.isEmpty()) {
				return null;
			}

			int color = colorOf(style);
			int flags = flagsOf(style);

			for (int i = 0; i < text.length();) {
				char ch = text.charAt(i);
				if (Character.isHighSurrogate(ch)) {
					if (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(i + 1))) {
						return ParseResult.error("Invalid Unicode input.", visible);
					}
				} else if (Character.isLowSurrogate(ch)) {
					return ParseResult.error("Invalid Unicode input.", visible);
				}

				int cp = text.codePointAt(i);
				i += Character.charCount(cp);

				if (cp == '\n') {
					ParseResult error = addLineBreak();
					if (error != null) {
						return error;
					}
					continue;
				}

				if (cp == '\t') {
					for (int j = 0; j < 4; j++) {
						ParseResult error = appendVisibleCodePoint(' ', color, flags);
						if (error != null) {
							return error;
						}
					}
					continue;
				}

				if (cp == '\u00A7') {
					return ParseResult.error("Legacy formatting codes are not allowed.", visible);
				}

				if (Character.isISOControl(cp)) {
					return ParseResult.error("Control characters are not allowed.", visible);
				}

				ParseResult error = appendVisibleCodePoint(cp, color, flags);
				if (error != null) {
					return error;
				}
			}

			return null;
		}

		private ParseResult appendVisibleCodePoint(int cp, int color, int flags) {
			if (currentText.isEmpty()) {
				currentColor = color;
				currentFlags = flags;
			} else if (currentColor != color || currentFlags != flags) {
				flushRun();
				currentColor = color;
				currentFlags = flags;
			}

			currentText.appendCodePoint(cp);
			visible++;
			if (visible > LoreMarkupParser.MAX_VISIBLE_CODEPOINTS) {
				return ParseResult.error("Lore is limited to 255 visible symbols.", visible);
			}

			return null;
		}

		private ParseResult addLineBreak() {
			flushRun();
			lines.add(new LoreLine(List.copyOf(currentRuns)));
			currentRuns.clear();

			if (lines.size() >= LoreMarkupParser.MAX_LINES) {
				return ParseResult.error("Too many lore lines.", visible);
			}

			return null;
		}

		private void flushRun() {
			if (currentText.isEmpty()) {
				return;
			}

			String text = currentText.toString();
			if (!currentRuns.isEmpty()) {
				LoreRun previous = currentRuns.getLast();
				if (previous.rgb() == currentColor && previous.flags() == currentFlags) {
					currentRuns.set(currentRuns.size() - 1, new LoreRun(previous.text() + text, currentColor, currentFlags));
					currentText.setLength(0);
					return;
				}
			}

			currentRuns.add(new LoreRun(text, currentColor, currentFlags));
			currentText.setLength(0);
		}

		private LoreDocument buildDocument() {
			flushRun();
			lines.add(new LoreLine(List.copyOf(currentRuns)));
			return new LoreDocument(trimTrailingEmptyLines(lines), visible);
		}
	}

	private static int colorOf(Style style) {
		TextColor color = style.getColor();
		return color == null ? LoreMarkupParser.DEFAULT_COLOR : color.getValue() & 0xFFFFFF;
	}

	private static int flagsOf(Style style) {
		int flags = 0;
		if (style.isBold()) {
			flags |= LoreRun.BOLD;
		}
		if (style.isItalic()) {
			flags |= LoreRun.ITALIC;
		}
		if (style.isUnderlined()) {
			flags |= LoreRun.UNDERLINED;
		}
		if (style.isStrikethrough()) {
			flags |= LoreRun.STRIKETHROUGH;
		}
		if (style.isObfuscated()) {
			flags |= LoreRun.OBFUSCATED;
		}
		return flags;
	}

	private static List<LoreLine> trimTrailingEmptyLines(List<LoreLine> lines) {
		int end = lines.size();
		while (end > 0 && lines.get(end - 1).isEmpty()) {
			end--;
		}

		return List.copyOf(lines.subList(0, end));
	}
}
