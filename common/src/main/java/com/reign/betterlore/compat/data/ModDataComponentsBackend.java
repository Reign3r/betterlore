package com.reign.betterlore.compat.data;

import net.minecraft.world.item.ItemStack;

/**
 * Version-neutral contract for the Minecraft NBT access used by Better Lore.
 *
 * <p>The release collector may relocate version-specific implementations of
 * this interface. Keep this contract free of version-conditioned code.</p>
 */
public interface ModDataComponentsBackend {
	String getString(ItemStack stack, String rootKey, String key);

	void setString(ItemStack stack, String rootKey, String key, String value);

	void removeString(ItemStack stack, String rootKey, String key);
}
