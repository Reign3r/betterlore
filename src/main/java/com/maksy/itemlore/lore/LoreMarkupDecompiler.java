package com.maksy.itemlore.lore;

import com.maksy.itemlore.ModDataComponents;
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
		String rawMarkup = stack.get(ModDataComponents.RAW_LORE_MARKUP);
		if (isValidLoreMarkup(rawMarkup)) {
			return rawMarkup;
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
		String rawMarkup = stack.get(ModDataComponents.RAW_NAME_MARKUP);
		if (isValidNameMarkup(rawMarkup)) {
			return rawMarkup;
		}

		Component customName = stack.get(DataComponents.CUSTOM_NAME);
		if (customName == null) {
			return "";
		}

		StringBuilder markup = new StringBuilder();
		appendComponent(markup, customName);
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
		boolean colored = color != null && color.getValue() != LoreMarkupParser.DEFAULT_COLOR;
		boolean bold = style.isBold();
		boolean italic = style.isItalic();
		boolean underlined = style.isUnderlined();
		boolean strikethrough = style.isStrikethrough();
		boolean obfuscated = style.isObfuscated();

		if (colored) {
			markup.append("[color:").append(LoreMarkupParser.formatHex(color.getValue())).append(']');
		}
		if (bold) {
			markup.append("[b]");
		}
		if (italic) {
			markup.append("[i]");
		}
		if (underlined) {
			markup.append("[u]");
		}
		if (strikethrough) {
			markup.append("[s]");
		}
		if (obfuscated) {
			markup.append("[o]");
		}

		appendEscaped(markup, text);

		if (obfuscated) {
			markup.append("[/o]");
		}
		if (strikethrough) {
			markup.append("[/s]");
		}
		if (underlined) {
			markup.append("[/u]");
		}
		if (italic) {
			markup.append("[/i]");
		}
		if (bold) {
			markup.append("[/b]");
		}
		if (colored) {
			markup.append("[/color]");
		}
	}

	private static void appendEscaped(StringBuilder markup, String text) {
		for (int i = 0; i < text.length();) {
			int cp = text.codePointAt(i);
			switch (cp) {
				case '\\' -> markup.append("\\\\");
				case '[' -> markup.append("\\[");
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
