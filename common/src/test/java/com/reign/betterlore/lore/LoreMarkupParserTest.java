package com.reign.betterlore.lore;

import org.junit.jupiter.api.Test;
import com.reign.betterlore.lore.quicktext.QuickTextSanitizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LoreMarkupParserTest {
	@Test
	void quickTextColorTagsDoNotCountAsVisibleSymbols() {
		ParseResult result = LoreMarkupParser.parse("<c #ff0000>red</c> normal");

		assertTrue(result.isSuccess());
		assertEquals(10, result.visibleCodePoints());
		assertEquals(2, result.document().lines().getFirst().runs().size());
		assertEquals(0xFF0000, result.document().lines().getFirst().runs().getFirst().rgb());
	}

	@Test
	void quickTextHexColorsAreCaseInsensitive() {
		ParseResult result = LoreMarkupParser.parse("<c #FF6600>Fire</c>");

		assertTrue(result.isSuccess());
		assertEquals(4, result.visibleCodePoints());
		assertEquals("Fire", result.document().lines().getFirst().runs().getFirst().text());
		assertEquals(0xFF6600, result.document().lines().getFirst().runs().getFirst().rgb());
	}

	@Test
	void quickTextGradientUsesLibraryOutputWithoutCountingTags() {
		ParseResult result = LoreMarkupParser.parse("<gr #ff6600 #aa1208>fire</gr>");

		assertTrue(result.isSuccess());
		assertEquals(4, result.visibleCodePoints());
		assertEquals("fire", result.document().lines().getFirst().runs().stream().map(LoreRun::text).reduce("", String::concat));
		assertTrue(result.document().lines().getFirst().runs().size() > 1);
		assertEquals(0xFF6600, result.document().lines().getFirst().runs().getFirst().rgb());
	}

	@Test
	void quickTextGradientCanUseMoreThanTwoColors() {
		ParseResult result = LoreMarkupParser.parse("<gr #ff0000 #00ff00 #0000ff>RGB</gr>");

		assertTrue(result.isSuccess());
		assertEquals(3, result.visibleCodePoints());
		assertEquals(0xFF0000, result.document().lines().getFirst().runs().getFirst().rgb());
		assertTrue(result.document().lines().getFirst().runs().size() > 1);
	}

	@Test
	void oldSquareColorSyntaxIsVisibleLiteralText() {
		ParseResult result = LoreMarkupParser.parse("[c:#ff0000]red[/c]");

		assertTrue(result.isSuccess());
		assertEquals(18, result.visibleCodePoints());
		LoreRun run = result.document().lines().getFirst().runs().getFirst();
		assertEquals("[c:#ff0000]red[/c]", run.text());
		assertEquals(LoreMarkupParser.DEFAULT_COLOR, run.rgb());
	}

	@Test
	void oldHashColorSyntaxIsVisibleLiteralText() {
		ParseResult result = LoreMarkupParser.parse("[#ff0000]red[/]");

		assertTrue(result.isSuccess());
		assertEquals(15, result.visibleCodePoints());
		assertEquals("[#ff0000]red[/]", result.document().lines().getFirst().runs().getFirst().text());
	}

	@Test
	void quickTextFormattingTagsApplyFlagsWithoutCountingAsVisibleSymbols() {
		ParseResult result = LoreMarkupParser.parse("<b><underlined>Marked</underlined></b>");

		assertTrue(result.isSuccess());
		assertEquals(6, result.visibleCodePoints());
		LoreRun run = result.document().lines().getFirst().runs().getFirst();
		assertEquals("Marked", run.text());
		assertTrue(run.bold());
		assertTrue(run.underlined());
	}

	@Test
	void quickTextGradientCanInheritFormattingTags() {
		ParseResult result = LoreMarkupParser.parse("<obf><gr #ff0000 #0000ff>ABC</gr></obf>");

		assertTrue(result.isSuccess());
		assertEquals(3, result.visibleCodePoints());
		assertTrue(result.document().lines().getFirst().runs().getFirst().obfuscated());
		assertEquals(0xFF0000, result.document().lines().getFirst().runs().getFirst().rgb());
		assertNotEquals(result.document().lines().getFirst().runs().getFirst().rgb(), result.document().lines().getFirst().runs().getLast().rgb());
	}

	@Test
	void decompilerEscapesAngleTagsAsLiteralText() {
		String normalized = LoreMarkupParser.toPreferredMarkup("literal <not_a_tag> text");

		assertEquals("literal <not_a_tag> text", normalized);
	}

	@Test
	void countsEmojiAsOneCodePoint() {
		ParseResult result = LoreMarkupParser.parse("A \uD83D\uDD25 B");

		assertTrue(result.isSuccess());
		assertEquals(5, result.visibleCodePoints());
	}

	@Test
	void parseNameUsesQuickTextColorAndGradientMarkupOnOneLine() {
		ParseResult result = LoreMarkupParser.parseName("<gr #a6c2ff #4651ff>Interesting...</gr>");

		assertTrue(result.isSuccess());
		assertEquals(14, result.visibleCodePoints());
		assertEquals(0xA6C2FF, result.document().lines().getFirst().runs().getFirst().rgb());
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
	void rejectsTooManyQuickTextTags() {
		ParseResult result = LoreMarkupParser.parse("<b></b>".repeat(LoreMarkupParser.MAX_COLOR_TAGS + 1));

		assertFalse(result.isSuccess());
		assertEquals("Too many markup tags.", result.errorMessage());
	}

	@Test
	void rejectsTooManyVisibleSymbols() {
		ParseResult result = LoreMarkupParser.parse("a".repeat(LoreMarkupParser.MAX_VISIBLE_CODEPOINTS + 1));

		assertFalse(result.isSuccess());
		assertEquals("Lore is limited to 255 visible symbols.", result.errorMessage());
	}

	@Test
	void acceptsExactlyTheVisibleSymbolLimit() {
		ParseResult result = LoreMarkupParser.parse("a".repeat(LoreMarkupParser.MAX_VISIBLE_CODEPOINTS));

		assertTrue(result.isSuccess());
		assertEquals(LoreMarkupParser.MAX_VISIBLE_CODEPOINTS, result.visibleCodePoints());
	}

	@Test
	void acceptsExactlyTheRawInputLimit() {
		QuickTextSanitizer.SanitizedInput result = QuickTextSanitizer.sanitizeLore(
				"a".repeat(LoreMarkupParser.MAX_RAW_CHARS)
		);

		assertTrue(result.isSuccess());
		assertEquals(LoreMarkupParser.MAX_RAW_CHARS, result.value().length());
	}

	@Test
	void rejectsRawInputBeyondTheLimit() {
		QuickTextSanitizer.SanitizedInput result = QuickTextSanitizer.sanitizeLore(
				"a".repeat(LoreMarkupParser.MAX_RAW_CHARS + 1)
		);

		assertFalse(result.isSuccess());
		assertEquals("Lore input is too long.", result.errorMessage());
	}

	@Test
	void acceptsExactlySixteenLoreLines() {
		ParseResult result = LoreMarkupParser.parse("a\n".repeat(LoreMarkupParser.MAX_LINES - 1) + "a");

		assertTrue(result.isSuccess());
		assertEquals(LoreMarkupParser.MAX_LINES, result.document().lines().size());
	}

	@Test
	void rejectsSeventeenLoreLines() {
		ParseResult result = LoreMarkupParser.parse("a\n".repeat(LoreMarkupParser.MAX_LINES) + "a");

		assertFalse(result.isSuccess());
		assertEquals("Too many lore lines.", result.errorMessage());
	}
}
