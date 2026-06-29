package com.maksy.itemlore.lore;

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
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null || lore.lines().isEmpty()) {
			return "";
		}

		StringBuilder markup = new StringBuilder();
		for (int i = 0; i < lore.lines().size(); i++) {
			if (i > 0) {
				markup.append('\n');
			}
			appendLine(markup, lore.lines().get(i));
		}

		return markup.toString();
	}

	private static void appendLine(StringBuilder markup, Component line) {
		line.visit((style, text) -> {
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

		if (colored) {
			markup.append("[color:").append(LoreMarkupParser.formatHex(color.getValue())).append(']');
		}

		appendEscaped(markup, text);

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
