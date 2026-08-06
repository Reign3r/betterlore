package com.reign.betterlore.world.compat;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Version-neutral contract for placed-item text persistence. */
public interface PlacedItemTextStorageBackend {
	void remember(Level level, BlockPos pos, ItemStack placedStack);

	void move(ServerLevel level, long sourcePos, long destinationPos);

	void restoreDrop(Level level, BlockPos pos, ItemStack droppedStack);

	/** Shared piston move primitive used by both the backend and unit tests. */
	static <T> boolean moveEntry(Long2ObjectMap<T> entries, long sourcePos, long destinationPos) {
		if (sourcePos == destinationPos) {
			return false;
		}

		T moved = entries.remove(sourcePos);
		if (moved == null) {
			return entries.remove(destinationPos) != null;
		}

		entries.put(destinationPos, moved);
		return true;
	}
}
