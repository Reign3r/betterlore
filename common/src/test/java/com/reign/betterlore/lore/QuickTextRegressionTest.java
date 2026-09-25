package com.reign.betterlore.lore;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs through the production facade on every exact loader/Minecraft target. */
class QuickTextRegressionTest {
	@Test
	void zeroGradientArgumentsTerminateAndPreserveTheWholeDraft() {
		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
			for (String tag : List.of("gr", "gradient", "hgr", "hard_gradient")) {
				for (String argument : List.of("0", "0:#ff0000", "0:red 1:blue", "\"0\"", "#ff0000 0 #0000ff")) {
					for (String suffix : List.of("", "</" + tag + "> after")) {
						String input = "<" + tag + " " + argument + ">Test" + suffix;
						ParseResult name = LoreMarkupParser.parseName(input);
						ParseResult lore = LoreMarkupParser.parse(input);
						assertTrue(name.isSuccess(), input);
						assertEquals(input, plainText(name), input);
						assertEquals(name, lore, input);
					}
				}
			}
		});
	}

	@Test
	void unmatchedClosingTagsDoNotDiscardTrailingText() {
		for (String input : List.of("before</gr>after", "<unknown>x</unknown>after", "<gr 0>x</gr><b>after</b>")) {
			ParseResult result = LoreMarkupParser.parse(input);
			assertTrue(result.isSuccess(), input);
			assertEquals(input.replace("<b>", "").replace("</b>", ""), plainText(result));
		}
	}

	@Test
	void nestedGradientAliasesFindTheOuterClosingTag() {
		for (String outer : List.of("gr", "gradient", "hgr", "hard_gradient")) {
			for (String inner : List.of("gr", "gradient", "hgr", "hard_gradient")) {
				String input = "<" + outer + " #ff0000 #0000ff>A<" + inner
						+ " #ffffff #000000>B</" + inner + ">C</" + outer + ">D";
				ParseResult result = LoreMarkupParser.parse(input);
				assertTrue(result.isSuccess(), input);
				assertEquals("ABCD", plainText(result), input);
				assertEquals(0x0000FF, colors(result).get(2), input);
				assertEquals(LoreMarkupParser.DEFAULT_COLOR, result.document().lines().getFirst().runs().getLast().rgb(), input);
			}
		}
	}

	@Test
	void allLoadersUseTheSameOklabDefaultAndExplicitColorModes() {
		ParseResult defaultGradient = LoreMarkupParser.parse("<gr #ff0000 #0000ff>ABC</gr>");
		assertEquals(defaultGradient, LoreMarkupParser.parse("<gradient type:oklab #ff0000 #0000ff>ABC</gradient>"));
		assertEquals(List.of(0xFF0000, 0x8C53A2, 0x0000FF), colors(defaultGradient));
		assertEquals(List.of(0xFF0000, 0xFF00FF, 0x0000FF), colors(
				LoreMarkupParser.parse("<gr type:hsv #ff0000 #0000ff>ABC</gr>")));
		assertEquals(LoreMarkupParser.parse("<hgr red blue>ABCD</hgr>"),
				LoreMarkupParser.parse("<gr type:hard red blue>ABCD</gr>"));
	}

	@Test
	void formattingAndUnicodeSurviveMultilineGradients() {
		ParseResult result = LoreMarkupParser.parse("<b><gr #ff0000 #0000ff>A\uD83D\uDD25\nB</gr></b>");
		assertTrue(result.isSuccess());
		assertEquals("A\uD83D\uDD25\nB", plainText(result));
		assertEquals(3, result.visibleCodePoints());
		assertEquals(2, result.document().lines().size());
		assertTrue(result.document().lines().stream().flatMap(line -> line.runs().stream()).allMatch(LoreRun::bold));
	}

	@Test
	void omittedAndUniversalClosingTagsHaveTheSameResult() {
		assertEquals(LoreMarkupParser.parse("<b><gr #ff0000 #0000ff>ABC</gr></b>"),
				LoreMarkupParser.parse("<b><gr #ff0000 #0000ff>ABC"));
		assertEquals(LoreMarkupParser.parse("<b><gradient #ff0000 #0000ff>ABC</gradient></b>"),
				LoreMarkupParser.parse("<b><gr #ff0000 #0000ff>ABC</></>"));
	}

	@Test
	void rainbowAndNamedColorsRemainAvailable() {
		assertEquals(LoreMarkupParser.parse("<rb 1 1 0>ABC</rb>"),
				LoreMarkupParser.parse("<rainbow f:1 s:1 o:0>ABC</rainbow>"));
		assertEquals(LoreMarkupParser.parse("<c red>ABC</c>"),
				LoreMarkupParser.parse("<color value:'#ff5555'>ABC</color>"));
	}

	@Test
	void boundedNestedFormattingAndLongGradientsTerminate() {
		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
			String nested = "<b>".repeat(128) + "X" + "</b>".repeat(128);
			ParseResult result = LoreMarkupParser.parse(nested);
			assertTrue(result.isSuccess());
			assertEquals("X", plainText(result));
			assertFalse(LoreMarkupParser.parse("<gr red blue>" + "x".repeat(256) + "</gr>").isSuccess());
			assertFalse(LoreMarkupParser.parse("<gr red blue>" + "x\n".repeat(17) + "</gr>").isSuccess());
		});
	}

	private static String plainText(ParseResult result) {
		return result.document().lines().stream()
				.map(line -> line.runs().stream().map(LoreRun::text).collect(Collectors.joining()))
				.collect(Collectors.joining("\n"));
	}

	private static List<Integer> colors(ParseResult result) {
		assertTrue(result.isSuccess());
		return result.document().lines().getFirst().runs().stream()
				.flatMap(run -> run.text().codePoints().mapToObj(ignored -> run.rgb())).toList();
	}
}
