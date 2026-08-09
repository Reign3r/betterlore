package com.reign.betterlore.world;

import com.reign.betterlore.compat.CompatibilityRuntime;
import com.reign.betterlore.lore.LoreComponents;
import com.reign.betterlore.lore.LoreDocument;
import com.reign.betterlore.lore.LoreMarkupParser;
import com.reign.betterlore.lore.LoreOwnership;
import com.reign.betterlore.lore.ParseResult;
import com.reign.betterlore.world.compat.PlacedItemTextStorageBackend;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Stable facade for text retained by ordinary blocks between placement and a
 * later item drop.
 *
 * <p>The persistence API changed between supported Minecraft versions. Its
 * implementation therefore lives behind a compatibility backend while this
 * class remains the single call site used by mixins and tests.</p>
 */
public final class PlacedItemTextStorage {
	private static final String BACKEND_CLASS_NAME =
			"com.reign.betterlore.world.compat.PlacedItemTextStorageBackendImpl";
	private static final java.util.Set<String> COPPER_GOLEM_STATUE_ITEM_IDS = java.util.Set.of(
			"minecraft:copper_golem_statue",
			"minecraft:exposed_copper_golem_statue",
			"minecraft:weathered_copper_golem_statue",
			"minecraft:oxidized_copper_golem_statue",
			"minecraft:waxed_copper_golem_statue",
			"minecraft:waxed_exposed_copper_golem_statue",
			"minecraft:waxed_weathered_copper_golem_statue",
			"minecraft:waxed_oxidized_copper_golem_statue"
	);

	/** Retained for source compatibility with the former SavedData class. */
	public PlacedItemTextStorage() {
	}

	public static void remember(Level level, BlockPos pos, ItemStack placedStack) {
		backend().remember(level, pos, placedStack);
	}

	/** Solid buckets retain text on the returned bucket rather than on their temporary block. */
	public static boolean shouldRememberPlacement(ItemStack placedStack) {
		return !(placedStack.getItem() instanceof SolidBucketItem);
	}

	/** Moves any saved text with a block and clears text displaced at its destination. */
	public static void move(ServerLevel level, long sourcePos, long destinationPos) {
		backend().move(level, sourcePos, destinationPos);
	}

	/** Applies and consumes saved text when the matching placed block item drops. */
	public static void restoreDrop(Level level, BlockPos pos, ItemStack droppedStack) {
		backend().restoreDrop(level, pos, droppedStack);
	}

	/**
	 * Rekeys one sparse entry without allocating or copying its value.
	 *
	 * <p>Vanilla installs moving piston blocks from farthest to nearest, so this
	 * also handles adjacent moved blocks without a temporary collection.</p>
	 */
	static <T> boolean moveEntry(Long2ObjectMap<T> entries, long sourcePos, long destinationPos) {
		return PlacedItemTextStorageBackend.moveEntry(entries, sourcePos, destinationPos);
	}

	/** Builds an atomic restore plan without ever promoting legacy or foreign lore to owned lore. */
	public static LoreRestorePlan planLoreRestore(
			List<Component> currentForeign,
			List<Component> storedForeign,
			String legacyLore,
			String ownedLore
	) {
		ParseResult parsedLegacy = LoreMarkupParser.parse(legacyLore);
		ParseResult parsedOwned = LoreMarkupParser.parse(ownedLore);
		if (!parsedLegacy.isSuccess() || !parsedOwned.isSuccess()) {
			return null;
		}

		List<Component> legacyForeign = LoreComponents.toComponents(parsedLegacy.document());
		List<Component> stored = new ArrayList<>(legacyForeign.size() + storedForeign.size());
		// Records from before ownership markers existed have no reliable provenance.
		stored.addAll(legacyForeign);
		stored.addAll(storedForeign);

		List<Component> mergedForeign = mergeForeignLines(currentForeign, stored);
		List<Component> ownedComponents = LoreComponents.toComponents(parsedOwned.document());
		if (LoreOwnership.compose(mergedForeign, List.of(), ownedComponents).size()
				> LoreComponents.MAX_VISIBLE_LORE_LINES) {
			return null;
		}
		return new LoreRestorePlan(mergedForeign, ownedLore, parsedOwned.document());
	}

	public static List<Component> mergeForeignLines(List<Component> current, List<Component> stored) {
		if (stored.isEmpty()) {
			return List.copyOf(current);
		}

		int[][] commonSuffix = new int[current.size() + 1][stored.size() + 1];
		for (int currentIndex = current.size() - 1; currentIndex >= 0; currentIndex--) {
			for (int storedIndex = stored.size() - 1; storedIndex >= 0; storedIndex--) {
				commonSuffix[currentIndex][storedIndex] = current.get(currentIndex).equals(stored.get(storedIndex))
						? commonSuffix[currentIndex + 1][storedIndex + 1] + 1
						: Math.max(
								commonSuffix[currentIndex + 1][storedIndex],
								commonSuffix[currentIndex][storedIndex + 1]
						);
			}
		}

		List<Component> merged = new ArrayList<>(
				current.size() + stored.size() - commonSuffix[0][0]
		);
		int currentIndex = 0;
		int storedIndex = 0;
		while (currentIndex < current.size() && storedIndex < stored.size()) {
			Component currentLine = current.get(currentIndex);
			Component storedLine = stored.get(storedIndex);
			if (currentLine.equals(storedLine)) {
				merged.add(currentLine);
				currentIndex++;
				storedIndex++;
			} else if (commonSuffix[currentIndex + 1][storedIndex]
					>= commonSuffix[currentIndex][storedIndex + 1]) {
				merged.add(currentLine);
				currentIndex++;
			} else {
				merged.add(storedLine);
				storedIndex++;
			}
		}
		merged.addAll(current.subList(currentIndex, current.size()));
		merged.addAll(stored.subList(storedIndex, stored.size()));
		return List.copyOf(merged);
	}

	/** A drop-time Better Lore edit is newer than the placed-block snapshot. */
	public static String selectOwnedLore(String currentOwned, String storedOwned) {
		return currentOwned == null || currentOwned.isEmpty() ? storedOwned : currentOwned;
	}

	/** Copper golem statues retain one block entity while scraping, waxing, or weathering. */
	public static boolean matchesStoredItem(String storedItemId, String droppedItemId) {
		return storedItemId.equals(droppedItemId)
				|| COPPER_GOLEM_STATUE_ITEM_IDS.contains(storedItemId)
				&& COPPER_GOLEM_STATUE_ITEM_IDS.contains(droppedItemId);
	}

	public record LoreRestorePlan(
			List<Component> foreignLore,
			String rawOwnedLore,
			LoreDocument ownedDocument
	) {
		public LoreRestorePlan {
			foreignLore = List.copyOf(foreignLore);
		}
	}

	private static PlacedItemTextStorageBackend backend() {
		return BackendHolder.INSTANCE;
	}

	private static final class BackendHolder {
		private static final PlacedItemTextStorageBackend INSTANCE = CompatibilityRuntime.instantiate(
				BACKEND_CLASS_NAME,
				PlacedItemTextStorageBackend.class
		);
	}
}
