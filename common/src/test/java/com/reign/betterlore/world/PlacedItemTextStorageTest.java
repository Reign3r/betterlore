package com.reign.betterlore.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import com.reign.betterlore.test.MinecraftTestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacedItemTextStorageTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureCodecRegistries();
	}

	@Test
	void solidBucketPlacementIsExcludedFromPositionBoundTextStorage() {
		assertFalse(PlacedItemTextStorage.shouldRememberPlacement(
				new ItemStack(Items.POWDER_SNOW_BUCKET)
		));
		assertTrue(PlacedItemTextStorage.shouldRememberPlacement(
				new ItemStack(Items.STONE)
		));
	}

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

	@Test
	void restoredForeignLoreNeverOverwritesLoreAddedToTheDrop() {
		Component current = Component.literal("Added while dropping");
		Component stored = Component.literal("Present before placement");

		assertEquals(
				List.of(current, stored),
				PlacedItemTextStorage.mergeForeignLines(List.of(current), List.of(stored))
		);
	}

	@Test
	void alreadyRestoredForeignSliceIsNotDuplicated() {
		Component stored = Component.literal("Stored");
		List<Component> current = List.of(Component.literal("Other"), stored, Component.literal("Tail"));

		assertEquals(current, PlacedItemTextStorage.mergeForeignLines(current, List.of(stored)));
	}

	@Test
	void interleavedStoredLoreIsRecognizedWithoutDuplicatingIt() {
		Component first = Component.literal("First");
		Component second = Component.literal("Second");
		Component inserted = Component.literal("Inserted elsewhere");

		assertEquals(
				List.of(first, inserted, second),
				PlacedItemTextStorage.mergeForeignLines(
						List.of(first, inserted, second),
						List.of(first, second)
				)
		);
	}

	@Test
	void partialStoredLoreIsMergedWithoutRepeatingMatchedLines() {
		Component first = Component.literal("First");
		Component second = Component.literal("Second");

		assertEquals(
				List.of(first, second),
				PlacedItemTextStorage.mergeForeignLines(List.of(first), List.of(first, second))
		);
		assertEquals(
				List.of(first, second),
				PlacedItemTextStorage.mergeForeignLines(List.of(second), List.of(first, second))
		);
	}

	@Test
	void newerDropOwnedLoreWinsOverStoredSnapshot() {
		assertEquals("Current", PlacedItemTextStorage.selectOwnedLore("Current", "Stored"));
		assertEquals("Stored", PlacedItemTextStorage.selectOwnedLore("", "Stored"));
		assertEquals("", PlacedItemTextStorage.selectOwnedLore("", ""));
	}

	@Test
	void copperGolemStatueVariantsShareOnlyTheirPlacedTextRecord() {
		assertTrue(PlacedItemTextStorage.matchesStoredItem(
				"minecraft:oxidized_copper_golem_statue",
				"minecraft:copper_golem_statue"
		));
		assertTrue(PlacedItemTextStorage.matchesStoredItem(
				"minecraft:copper_golem_statue",
				"minecraft:waxed_exposed_copper_golem_statue"
		));
		assertFalse(PlacedItemTextStorage.matchesStoredItem(
				"minecraft:oxidized_copper_golem_statue",
				"minecraft:oxidized_copper"
		));
		assertFalse(PlacedItemTextStorage.matchesStoredItem(
				"other_mod:copper_golem_statue",
				"minecraft:copper_golem_statue"
		));
	}

	@Test
	void oldPlacedRecordsRestoreUnprovenLoreAsForeign() {
		PlacedItemTextStorage.LoreRestorePlan plan = PlacedItemTextStorage.planLoreRestore(
				List.of(),
				List.of(),
				"<i>Legacy lore</i>",
				""
		);

		assertEquals(List.of("Legacy lore"),
				plan.foreignLore().stream().map(Component::getString).toList());
		assertEquals("", plan.rawOwnedLore());
	}

	@Test
	void restorePlanKeepsCurrentAndStoredForeignBeforeOwnedLore() {
		Component current = Component.literal("Current foreign");
		Component stored = Component.literal("Stored foreign");
		PlacedItemTextStorage.LoreRestorePlan plan = PlacedItemTextStorage.planLoreRestore(
				List.of(current),
				List.of(stored),
				"",
				"<b>Owned</b>"
		);

		assertEquals(List.of(current, stored), plan.foreignLore());
		assertEquals("<b>Owned</b>", plan.rawOwnedLore());
	}

	@Test
	void restorePlanRefusesCombinedOverflow() {
		List<Component> current = java.util.stream.IntStream.range(0, 255)
				.mapToObj(index -> (Component) Component.literal("Foreign " + index))
				.toList();

		assertNull(PlacedItemTextStorage.planLoreRestore(
				current,
				List.of(),
				"",
				"Owned"
		));
	}
}
