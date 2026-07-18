package com.reign.betterlore.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkupInsertionTest {
	@Test
	void insertsEmptyPairAtCursor() {
		var result = MarkupInsertion.wrap("Fire", 2, 2, "<b>", "</b>", 100);
		assertTrue(result.changed());
		assertEquals("Fi<b></b>re", result.text());
		assertEquals(5, result.cursor());
		assertEquals(5, result.anchor());
	}

	@Test
	void wrapsForwardSelectionAndPreservesIt() {
		var result = MarkupInsertion.wrap("Sunlit Relic", 6, 0, "<gr #f00 #0f0>", "</gr>", 100);
		assertEquals("<gr #f00 #0f0>Sunlit</gr> Relic", result.text());
		assertEquals("<gr #f00 #0f0>".length() + 6, result.cursor());
		assertEquals("<gr #f00 #0f0>".length(), result.anchor());
	}

	@Test
	void wrapsReverseSelectionWithoutReversingText() {
		var result = MarkupInsertion.wrap("abcdef", 1, 4, "<i>", "</i>", 100);
		assertEquals("a<i>bcd</i>ef", result.text());
		assertEquals(7, result.cursor());
		assertEquals(4, result.anchor());
	}

	@Test
	void clampsInvalidCursorPositions() {
		var result = MarkupInsertion.wrap("abc", -20, 99, "<u>", "</u>", 100);
		assertEquals("<u>abc</u>", result.text());
	}

	@Test
	void refusesInsertionThatWouldExceedRawLimit() {
		var result = MarkupInsertion.wrap("12345", 2, 2, "<b>", "</b>", 10);
		assertFalse(result.changed());
		assertEquals("12345", result.text());
	}
}
