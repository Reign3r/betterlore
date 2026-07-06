package com.reign.betterlore.lore;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoreMarkupDecompilerTest {
	@Test
	void decompilerOutputsQuickTextColorForItemNames() {
		Component component = Component.literal("Fire").withStyle(style -> style.withColor(TextColor.fromRgb(0xFF6600)));

		assertEquals("<c #ff6600>Fire</c>", LoreMarkupDecompiler.toSafeComponentMarkup(component));
	}

	@Test
	void decompilerOutputsQuickTextFormattingTags() {
		Component component = Component.literal("Marked").withStyle(style -> style.withBold(true).withUnderlined(true));

		assertEquals("<b><underlined>Marked</underlined></b>", LoreMarkupDecompiler.toSafeComponentMarkup(component));
	}

	@Test
	void decompilerEscapesLiteralAngleBrackets() {
		assertEquals("\\<literal>", LoreMarkupDecompiler.toSafeComponentMarkup(Component.literal("<literal>")));
	}
}
