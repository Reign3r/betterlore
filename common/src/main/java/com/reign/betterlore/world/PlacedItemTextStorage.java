package com.reign.betterlore.world;

import com.reign.betterlore.compat.CompatibilityRuntime;
import com.reign.betterlore.world.compat.PlacedItemTextStorageBackend;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

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

	/** Retained for source compatibility with the former SavedData class. */
	public PlacedItemTextStorage() {
	}

	public static void remember(Level level, BlockPos pos, ItemStack placedStack) {
		backend().remember(level, pos, placedStack);
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
