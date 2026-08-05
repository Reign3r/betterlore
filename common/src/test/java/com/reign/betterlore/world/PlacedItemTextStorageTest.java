package com.reign.betterlore.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacedItemTextStorageTest {
	@Test
	void movesExistingEntryWithoutCopyingIt() {
		Long2ObjectOpenHashMap<Object> entries = new Long2ObjectOpenHashMap<>();
		Object text = new Object();
		entries.put(10L, text);

		assertTrue(PlacedItemTextStorage.moveEntry(entries, 10L, 11L));
		assertNull(entries.get(10L));
		assertSame(text, entries.get(11L));
		assertEquals(1, entries.size());
	}

	@Test
	void farthestFirstMovesAdjacentEntriesWithoutOverwritingThem() {
		Long2ObjectOpenHashMap<String> entries = new Long2ObjectOpenHashMap<>();
		entries.put(10L, "near");
		entries.put(11L, "far");

		assertTrue(PlacedItemTextStorage.moveEntry(entries, 11L, 12L));
		assertTrue(PlacedItemTextStorage.moveEntry(entries, 10L, 11L));

		assertNull(entries.get(10L));
		assertEquals("near", entries.get(11L));
		assertEquals("far", entries.get(12L));
		assertEquals(2, entries.size());
	}

	@Test
	void undecoratedBlockClearsDisplacedDestinationText() {
		Long2ObjectOpenHashMap<String> entries = new Long2ObjectOpenHashMap<>();
		entries.put(11L, "stale destination");

		assertTrue(PlacedItemTextStorage.moveEntry(entries, 10L, 11L));
		assertTrue(entries.isEmpty());
	}

	@Test
	void missingMoveDoesNotDirtyStorage() {
		Long2ObjectOpenHashMap<String> entries = new Long2ObjectOpenHashMap<>();

		assertFalse(PlacedItemTextStorage.moveEntry(entries, 10L, 11L));
		assertTrue(entries.isEmpty());
	}

	@Test
	void samePositionIsANoOp() {
		Long2ObjectOpenHashMap<String> entries = new Long2ObjectOpenHashMap<>();
		entries.put(10L, "text");

		assertFalse(PlacedItemTextStorage.moveEntry(entries, 10L, 10L));
		assertEquals("text", entries.get(10L));
	}
}
