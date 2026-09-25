package com.reign.betterlore.lore.quicktext;

import com.reign.betterlore.lore.LoreMarkupParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EditorMarkupTest {
	private static final String OLD = "<gr type:legacy_oklab #ff6600 #aa1208>Titanfall</gr>";
	private static final String VISIBLE = "<gr #ff6600 #aa1208>Titanfall</gr>";

	@Test
	void removesDefaultModeFromTheReportedNestedExpressionWithoutChangingFormatting() {
		String raw = "<gr type:oklab #7f00ff #e6e6fa><b><i>Titanfall<gr type:oklab #ff6600 #aa1208>"
				+ "<gr type:oklab #ff6600 #aa1208><underlined><st><c #ff6600>vfsvfdvs<c #ff6600></c></c>"
				+ "</st></underlined></gr></gr>\\</gr></i></b></gr>";
		EditorMarkup editor = load(raw);
		assertEquals(raw.replace(" type:oklab", ""), editor.text());
		assertEquals(editor.text(), editor.raw());
		assertEquals(LoreMarkupParser.parse(raw), LoreMarkupParser.parse(editor.raw()));
	}

	@Test
	void everySupportedModeKeepsItsColorsBehindOrdinaryTags() {
		for (String mode : List.of("oklab", "hsv", "hvs", "hard", "legacy_oklab", "legacy_hsv", "legacy_hard")) {
			String raw = "<b><gr type:" + mode + " #ff6600 #aa1208>A😀BC</gr></b>";
			EditorMarkup editor = load(raw);
			assertFalse(editor.text().contains("type:"), mode);
			assertFalse(editor.text().contains("legacy_"), mode);
			assertEquals(LoreMarkupParser.parse(raw), LoreMarkupParser.parse(editor.raw()), mode);
			assertEquals(LoreMarkupParser.parseName(raw), LoreMarkupParser.parseName(editor.raw()), mode);
		}
		String rainbow = "<rb type:legacy_hsv f:0.5 s:0.6 o:0.2>ABCD</rb>";
		EditorMarkup editor = load(rainbow);
		assertEquals("<rb f:0.5 s:0.6 o:0.2>ABCD</rb>", editor.text());
		assertEquals(LoreMarkupParser.parse(rainbow), LoreMarkupParser.parse(editor.raw()));
	}

	@Test
	void vanillaEchoUsesTheProjectedTextOfMigratedMarkup() {
		assertEquals(VISIBLE, EditorMarkup.visibleText(OLD));
		assertEquals(VISIBLE, EditorMarkup.visibleText(VISIBLE));
	}

	@Test
	void editingTextAndColorsRetainsTheOriginalGradientMode() {
		EditorMarkup editor = load(OLD);
		assertTrue(editor.edit(VISIBLE.replace("Titanfall", "Edited")));
		assertEquals(OLD.replace("Titanfall", "Edited"), editor.raw());
		assertTrue(editor.edit(editor.text().replace("#ff6600", "#112233")));
		assertEquals(OLD.replace("Titanfall", "Edited").replace("#ff6600", "#112233"), editor.raw());
	}

	@Test
	void wrappingSelectedNestedGradientsRetainsTheirIndependentModes() {
		String raw = OLD.replace("Titanfall", "A<gr type:legacy_hsv red blue>BC</gr>D");
		EditorMarkup editor = load(raw);
		assertTrue(editor.wrap(0, editor.text().length(), "<b>", "</b>"));
		assertEquals(LoreMarkupParser.parse("<b>" + raw + "</b>"), LoreMarkupParser.parse(editor.raw()));
		assertFalse(editor.text().contains("type:"));
		assertTrue(editor.edit(load(raw).text())); // undo the wrapper
		assertEquals(raw, editor.raw());
	}

	@Test
	void insertingAnIdenticalGradientOutsideTheOldOneDoesNotStealItsMode() {
		EditorMarkup editor = load(OLD);
		assertTrue(editor.edit("<gr #ff6600 #aa1208>" + VISIBLE + "</gr>"));
		assertEquals("<gr #ff6600 #aa1208>" + OLD + "</gr>", editor.raw());
	}

	@Test
	void deletingOneGradientDoesNotTransferItsModeToTheNext() {
		String other = "<gr type:legacy_hsv red blue>Other</gr>";
		EditorMarkup editor = load(OLD + other);
		assertTrue(editor.edit(load(other).text()));
		assertEquals(other, editor.raw());
		assertTrue(editor.edit("Plain text"));
		assertEquals("Plain text", editor.raw());
	}

	@Test
	void undoAndRedoRestoreHiddenModesAndLoadingAnotherItemClearsHistory() {
		EditorMarkup editor = load(OLD);
		assertTrue(editor.edit("Replacement"));
		assertTrue(editor.edit(VISIBLE));
		assertEquals(OLD, editor.raw());
		assertTrue(editor.edit("Replacement"));
		assertEquals("Replacement", editor.raw());
		editor.load(VISIBLE);
		assertEquals(VISIBLE, editor.raw());
	}

	@Test
	void explicitlyPastedModesOverrideExistingHiddenModes() {
		EditorMarkup editor = load(OLD);
		assertTrue(editor.edit(OLD.replace("legacy_oklab", "oklab")));
		assertEquals(VISIBLE, editor.raw());
		assertTrue(editor.edit(OLD.replace("legacy_oklab", "legacy_hsv")));
		assertEquals(OLD.replace("legacy_oklab", "legacy_hsv"), editor.raw());
	}

	@Test
	void literalEscapedUnknownAndMalformedTagsRemainUnchanged() {
		for (String raw : List.of("\\<gr type:oklab red blue>Text", "<unknown type:oklab>Text</unknown>",
				"<gr type:oklab 0>Text</gr>", "<unknown<gr type:oklab red blue>Text", "Text type:oklab")) {
			EditorMarkup editor = load(raw);
			assertEquals(raw, editor.text());
			assertEquals(raw, editor.raw());
		}
	}

	@Test
	void temporarilyInvalidColorDoesNotDiscardHiddenMode() {
		EditorMarkup editor = load(OLD);
		assertTrue(editor.edit(VISIBLE.replace("#ff6600", "#")));
		assertEquals(OLD.replace("#ff6600", "#"), editor.raw());
		assertTrue(editor.edit(VISIBLE.replace("#ff6600", "#123456")));
		assertEquals(OLD.replace("#ff6600", "#123456"), editor.raw());
	}

	@Test
	void hiddenAttributesCannotOverflowThePacketLimit() {
		EditorMarkup editor = load(OLD);
		assertFalse(editor.edit(VISIBLE + "x".repeat(LoreMarkupParser.MAX_RAW_CHARS - VISIBLE.length())));
		assertEquals(OLD, editor.raw());
		assertFalse(editor.wrap(0, editor.text().length(), "x".repeat(4096), ""));
		assertEquals(OLD, editor.raw());
		String boundary = OLD + "x".repeat(LoreMarkupParser.MAX_RAW_CHARS - OLD.length());
		editor.load(boundary);
		assertEquals(boundary, editor.raw());
		assertFalse(editor.text().contains("type:"));
	}

	@Test
	void cursorPositionsUseVisibleTextAndRepeatedReadsDoNotRebuildTheDraft() {
		EditorMarkup editor = load(OLD);
		assertEquals(VISIBLE.length(), EditorMarkup.visiblePosition(OLD, OLD.length()));
		assertEquals(VISIBLE.indexOf("Titanfall"), EditorMarkup.visiblePosition(OLD, OLD.indexOf("Titanfall")));
		String raw = editor.raw();
		for (int i = 0; i < 100; i++) {
			assertTrue(editor.edit(VISIBLE));
			assertSame(raw, editor.raw());
		}
	}

	private static EditorMarkup load(String raw) {
		EditorMarkup editor = new EditorMarkup();
		editor.load(raw);
		return editor;
	}
}
