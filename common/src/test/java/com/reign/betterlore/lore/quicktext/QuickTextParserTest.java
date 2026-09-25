package com.reign.betterlore.lore.quicktext;

import com.reign.betterlore.lore.LoreRun;
import com.reign.betterlore.lore.ParseResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickTextParserTest {
	@Test
	void sharedParserSupportsQuickTextColorAliases() {
		ParseResult result = QuickTextParser.parse("<color #FF6600>Fire</color>");

		assertTrue(result.isSuccess());
		LoreRun run = result.document().lines().getFirst().runs().getFirst();
		assertEquals("Fire", run.text());
		assertEquals(0xFF6600, run.rgb());
	}

	@Test
	void sharedParserUsesSmoothGradientByDefault() {
		ParseResult result = QuickTextParser.parse("<gr #ff0000 #0000ff>ABC</gr>");

		assertTrue(result.isSuccess());
		assertEquals(3, result.visibleCodePoints());
		assertEquals(0xFF0000, result.document().lines().getFirst().runs().getFirst().rgb());
		assertEquals(0x0000FF, result.document().lines().getFirst().runs().getLast().rgb());
		assertNotEquals(0xFF0000, result.document().lines().getFirst().runs().get(1).rgb());
	}

	@Test
	void sharedParserSupportsHardGradientTags() {
		ParseResult result = QuickTextParser.parse("<hgr #ff0000 #0000ff>ABCD</hgr>");

		assertTrue(result.isSuccess());
		assertEquals(4, result.visibleCodePoints());
		assertEquals(0xFF0000, result.document().lines().getFirst().runs().getFirst().rgb());
		assertEquals(0x0000FF, result.document().lines().getFirst().runs().getLast().rgb());
	}

	@Test
	void sharedParserSupportsTypeHardModifier() {
		ParseResult result = QuickTextParser.parse("<gradient #ff0000 #0000ff type:hard>AB</gradient>");

		assertTrue(result.isSuccess());
		assertEquals(0xFF0000, result.document().lines().getFirst().runs().getFirst().rgb());
		assertEquals(0x0000FF, result.document().lines().getFirst().runs().getLast().rgb());
	}
	@Test
	void sharedParserSupportsAllFormattingTags() {
		ParseResult result = QuickTextParser.parse("<b>B</b><i>I</i><underlined>U</underlined><st>S</st><obf>O</obf>");

		assertTrue(result.isSuccess());
		var runs = result.document().lines().getFirst().runs();
		assertEquals("B", runs.get(0).text());
		assertTrue(runs.get(0).bold());
		assertEquals("I", runs.get(1).text());
		assertTrue(runs.get(1).italic());
		assertEquals("U", runs.get(2).text());
		assertTrue(runs.get(2).underlined());
		assertEquals("S", runs.get(3).text());
		assertTrue(runs.get(3).strikethrough());
		assertEquals("O", runs.get(4).text());
		assertTrue(runs.get(4).obfuscated());
	}

	@Test
	void sharedParserTreatsLegacySquareTagsAsLiteralText() {
		ParseResult result = QuickTextParser.parse("[c:#ff6600]Fire[/c]");

		assertTrue(result.isSuccess());
		LoreRun run = result.document().lines().getFirst().runs().getFirst();
		assertEquals("[c:#ff6600]Fire[/c]", run.text());
		assertEquals(0xAAAAAA, run.rgb());
	}

	@Test
	void sharedParserDropsUnsafeTagsAsLiteralText() {
		ParseResult result = QuickTextParser.parse("<click run_command:/kill @a>bad</click>");

		assertTrue(result.isSuccess());
		String text = result.document().lines().getFirst().runs().stream()
				.map(LoreRun::text)
				.reduce("", String::concat);
		assertEquals("<click run_command:/kill @a>bad</click>", text);
		assertFalse(result.document().lines().getFirst().runs().getFirst().bold());
	}

}
