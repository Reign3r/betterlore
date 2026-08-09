package com.reign.betterlore.lore;

import com.reign.betterlore.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class LoreMarkupDecompiler {
	private LoreMarkupDecompiler() {
	}

	public static String toSafeMarkup(ItemStack stack) {
		return toSafeOwnedLoreMarkup(stack);
	}

	public static String toSafeLoreMarkup(ItemStack stack) {
		return toSafeOwnedLoreMarkup(stack);
	}

	public static String toSafeNameMarkup(ItemStack stack) {
		String rawMarkup = ModDataComponents.getRawNameMarkup(stack);
		if (isValidNameMarkup(rawMarkup)) {
			return LoreMarkupParser.toPreferredMarkup(rawMarkup);
		}

		Component customName = stack.get(DataComponents.CUSTOM_NAME);
		if (customName == null) {
			return "";
		}

		return toSafeComponentMarkup(customName);
	}

	/** Preserves the exact visible styling of one parsed lore line as QuickText. */
	public static String toSafeLineMarkup(LoreLine line) {
		Objects.requireNonNull(line, "line");
		StringBuilder markup = new StringBuilder();
		for (LoreRun run : line.runs()) {
			appendRun(markup, run);
		}
		return markup.toString();
	}

	/** Returns only lore whose Better Lore ownership can be proven. */
	public static String toSafeOwnedLoreMarkup(ItemStack stack) {
		String rawMarkup = ModDataComponents.getRawLoreMarkup(stack);
		ItemLore lore = stack.get(DataComponents.LORE);
		List<Component> lines = lore == null ? List.of() : lore.lines();
		return toSafeOwnedLoreMarkup(
				rawMarkup,
				lines,
				!ModDataComponents.hasCurrentLoreOwnership(stack)
		);
	}

	static String toSafeOwnedLoreMarkup(String rawMarkup, List<Component> lines) {
		return toSafeOwnedLoreMarkup(rawMarkup, lines, true);
	}

	static String toSafeOwnedLoreMarkup(
			String rawMarkup,
			List<Component> lines,
			boolean allowLegacyMatch
	) {
		List<Component> expectedOwned = parsedLoreComponents(rawMarkup);
		List<Component> ownedLines = LoreOwnership.ownedLines(
				lines,
				allowLegacyMatch ? expectedOwned : List.of()
		);
		if (!expectedOwned.isEmpty() && ownedLines.equals(expectedOwned)) {
			return LoreMarkupParser.toPreferredMarkup(rawMarkup);
		}
		return toSafeComponentListMarkup(ownedLines);
	}

	private static List<Component> parsedLoreComponents(String rawMarkup) {
		if (!isValidLoreMarkup(rawMarkup)) {
			return List.of();
		}
		return LoreComponents.toComponents(LoreMarkupParser.parse(rawMarkup).document());
	}

	static String toSafeComponentMarkup(Component component) {
		StringBuilder markup = new StringBuilder();
		appendComponent(markup, component);
		return markup.toString();
	}

	/** Decompiles an exact list of visible lore components without consulting item metadata. */
	public static String toSafeComponentListMarkup(List<Component> lines) {
		Objects.requireNonNull(lines, "lines");
		StringBuilder markup = new StringBuilder();
		for (int index = 0; index < lines.size(); index++) {
			if (index > 0) {
				markup.append('\n');
			}
			appendComponent(markup, lines.get(index));
		}
		return markup.toString();
	}

	private static boolean isValidLoreMarkup(String rawMarkup) {
		return rawMarkup != null && LoreMarkupParser.parse(rawMarkup).isSuccess();
	}

	private static boolean isValidNameMarkup(String rawMarkup) {
		return rawMarkup != null && LoreMarkupParser.parseName(rawMarkup).isSuccess();
	}

	private static void appendComponent(StringBuilder markup, Component component) {
		component.visit((style, text) -> {
			appendSegment(markup, style, text);
			return Optional.empty();
		}, Style.EMPTY);
	}

	private static void appendSegment(StringBuilder markup, Style style, String text) {
		if (text.isEmpty()) {
			return;
		}

		TextColor color = style.getColor();
		for (MarkupFormat format : MarkupFormat.values()) {
			if (isActive(format, style, color)) {
				appendOpening(markup, format, color);
			}
		}

		appendEscaped(markup, text);

		MarkupFormat[] formats = MarkupFormat.values();
		for (int i = formats.length - 1; i >= 0; i--) {
			MarkupFormat format = formats[i];
			if (isActive(format, style, color)) {
				appendClosing(markup, format);
			}
		}
	}

	private static void appendRun(StringBuilder markup, LoreRun run) {
		if (run.text().isEmpty()) {
			return;
		}

		if (run.bold()) {
			markup.append("<b>");
		}
		if (run.italic()) {
			markup.append("<i>");
		}
		if (run.underlined()) {
			markup.append("<underlined>");
		}
		if (run.strikethrough()) {
			markup.append("<st>");
		}
		if (run.obfuscated()) {
			markup.append("<obf>");
		}
		if (run.rgb() != LoreMarkupParser.DEFAULT_COLOR) {
			markup.append("<c ").append(LoreMarkupParser.formatHex(run.rgb())).append('>');
		}

		appendEscaped(markup, run.text());

		if (run.rgb() != LoreMarkupParser.DEFAULT_COLOR) {
			markup.append("</c>");
		}
		if (run.obfuscated()) {
			markup.append("</obf>");
		}
		if (run.strikethrough()) {
			markup.append("</st>");
		}
		if (run.underlined()) {
			markup.append("</underlined>");
		}
		if (run.italic()) {
			markup.append("</i>");
		}
		if (run.bold()) {
			markup.append("</b>");
		}
	}

	private static boolean isActive(MarkupFormat format, Style style, TextColor color) {
		return switch (format) {
			case COLOR -> color != null && color.getValue() != LoreMarkupParser.DEFAULT_COLOR;
			case BOLD -> style.isBold();
			case ITALIC -> style.isItalic();
			case UNDERLINED -> style.isUnderlined();
			case STRIKETHROUGH -> style.isStrikethrough();
			case OBFUSCATED -> style.isObfuscated();
		};
	}

	private static void appendOpening(StringBuilder markup, MarkupFormat format, TextColor color) {
		switch (format) {
			case COLOR -> markup.append("<c ").append(LoreMarkupParser.formatHex(color.getValue())).append('>');
			case BOLD -> markup.append("<b>");
			case ITALIC -> markup.append("<i>");
			case UNDERLINED -> markup.append("<underlined>");
			case STRIKETHROUGH -> markup.append("<st>");
			case OBFUSCATED -> markup.append("<obf>");
		}
	}

	private static void appendClosing(StringBuilder markup, MarkupFormat format) {
		switch (format) {
			case COLOR -> markup.append("</c>");
			case BOLD -> markup.append("</b>");
			case ITALIC -> markup.append("</i>");
			case UNDERLINED -> markup.append("</underlined>");
			case STRIKETHROUGH -> markup.append("</st>");
			case OBFUSCATED -> markup.append("</obf>");
		}
	}

	private enum MarkupFormat {
		COLOR,
		BOLD,
		ITALIC,
		UNDERLINED,
		STRIKETHROUGH,
		OBFUSCATED
	}

	private static void appendEscaped(StringBuilder markup, String text) {
		for (int i = 0; i < text.length();) {
			int cp = text.codePointAt(i);
			switch (cp) {
				case '\\' -> markup.append("\\\\");
				case '<' -> markup.append("\\<");
				case '\n', '\r', '\t' -> markup.append(' ');
				case '\u00A7' -> markup.append('?');
				default -> {
					if (!Character.isISOControl(cp)) {
						markup.appendCodePoint(cp);
					}
				}
			}
			i += Character.charCount(cp);
		}
	}
}
