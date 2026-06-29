package com.maksy.itemlore.lore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoreMarkupParserTest {
	@Test
	void colorTagsDoNotCountAsVisibleSymbols() {
		ParseResult result = LoreMarkupParser.parse("[color:#ff0000]red[/color] normal");

		assertTrue(result.isSuccess());
		assertEquals(10, result.visibleCodePoints());
		assertEquals(2, result.document().lines().getFirst().runs().size());
		assertEquals(0xFF0000, result.document().lines().getFirst().runs().getFirst().rgb());
	}

	@Test
	void colorTagsAreCaseInsensitive() {
		ParseResult result = LoreMarkupParser.parse("[COLOR:#FF6600]Fire[/COLOR]");

		assertTrue(result.isSuccess());
		assertEquals(4, result.visibleCodePoints());
		assertEquals("Fire", result.document().lines().getFirst().runs().getFirst().text());
		assertEquals(0xFF6600, result.document().lines().getFirst().runs().getFirst().rgb());
	}

	@Test
	void gradientTagInterpolatesFirstAndLastVisibleSymbols() {
		ParseResult result = LoreMarkupParser.parse("[gradient:#ff6600]fire[/gradient:#aa1208]");

		assertTrue(result.isSuccess());
		assertEquals(4, result.visibleCodePoints());
		assertEquals(4, result.document().lines().getFirst().runs().size());
		assertEquals(0xFF6600, result.document().lines().getFirst().runs().getFirst().rgb());
		assertEquals(0xAA1208, result.document().lines().getFirst().runs().getLast().rgb());
	}

	@Test
	void malformedColorTagIsVisibleLiteralText() {
		ParseResult result = LoreMarkupParser.parse("[color:#gggggg]oops");

		assertTrue(result.isSuccess());
		assertEquals(19, result.visibleCodePoints());
		assertEquals("[color:#gggggg]oops", result.document().lines().getFirst().runs().getFirst().text());
	}

	@Test
	void oldColorTagSyntaxIsVisibleLiteralText() {
		ParseResult result = LoreMarkupParser.parse("[#ff0000]red[/]");

		assertTrue(result.isSuccess());
		assertEquals(15, result.visibleCodePoints());
		assertEquals("[#ff0000]red[/]", result.document().lines().getFirst().runs().getFirst().text());
	}

	@Test
	void countsEmojiAsOneCodePoint() {
		ParseResult result = LoreMarkupParser.parse("A \uD83D\uDD25 B");

		assertTrue(result.isSuccess());
		assertEquals(5, result.visibleCodePoints());
	}

	@Test
	void parseNameUsesSameColorAndGradientMarkupOnOneLine() {
		ParseResult result = LoreMarkupParser.parseName("[gradient:#a6c2ff]Interesting...[/gradient:#4651ff]");

		assertTrue(result.isSuccess());
		assertEquals(14, result.visibleCodePoints());
		assertEquals(0xA6C2FF, result.document().lines().getFirst().runs().getFirst().rgb());
		assertEquals(0x4651FF, result.document().lines().getFirst().runs().getLast().rgb());
	}

	@Test
	void parseNameReplacesManualLineBreaksWithSpaces() {
		ParseResult result = LoreMarkupParser.parseName("First\nSecond");

		assertTrue(result.isSuccess());
		assertEquals(12, result.visibleCodePoints());
		assertEquals("First Second", result.document().lines().getFirst().runs().getFirst().text());
	}

	@Test
	void rejectsLegacySectionFormatting() {
		ParseResult result = LoreMarkupParser.parse("Bad \u00A7cRed");

		assertFalse(result.isSuccess());
		assertEquals("Legacy formatting codes are not allowed.", result.errorMessage());
	}

	@Test
	void rejectsTooManyVisibleSymbols() {
		ParseResult result = LoreMarkupParser.parse("a".repeat(LoreMarkupParser.MAX_VISIBLE_CODEPOINTS + 1));

		assertFalse(result.isSuccess());
		assertEquals("Lore is limited to 255 visible symbols.", result.errorMessage());
	}
}
