package com.reign.betterlore.lore;

import com.reign.betterlore.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.Optional;

public final class LoreMarkupDecompiler {
	private LoreMarkupDecompiler() {
	}

	public static String toSafeMarkup(ItemStack stack) {
		return toSafeLoreMarkup(stack);
	}

	public static String toSafeLoreMarkup(ItemStack stack) {
		String rawMarkup = ModDataComponents.getRawLoreMarkup(stack);
		if (isValidLoreMarkup(rawMarkup)) {
			return LoreMarkupParser.toPreferredMarkup(rawMarkup);
		}

		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null || lore.lines().isEmpty()) {
			return "";
		}

		StringBuilder markup = new StringBuilder();
		for (int i = 0; i < lore.lines().size(); i++) {
			if (i > 0) {
				markup.append('\n');
			}
			appendComponent(markup, lore.lines().get(i));
		}

		return markup.toString();
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

	static String toSafeComponentMarkup(Component component) {
		StringBuilder markup = new StringBuilder();
		appendComponent(markup, component);
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
