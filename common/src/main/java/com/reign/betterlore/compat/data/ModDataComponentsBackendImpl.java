package com.reign.betterlore.compat.data;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Minecraft-version-specific implementation selected by CompatibilityRuntime. */
public final class ModDataComponentsBackendImpl implements ModDataComponentsBackend {
	public ModDataComponentsBackendImpl() {
	}

	@Override
	public String getString(ItemStack stack, String rootKey, String key) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null || customData.isEmpty()) {
			return null;
		}

		return ModDataComponentsTagOps.getString(customData.copyTag(), rootKey, key);
	}

	@Override
	public int getInt(ItemStack stack, String rootKey, String key, int fallback) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null || customData.isEmpty()) {
			return fallback;
		}

		return ModDataComponentsTagOps.getInt(customData.copyTag(), rootKey, key, fallback);
	}

	@Override
	public void setString(ItemStack stack, String rootKey, String key, String value) {
		CompoundTag root = ModDataComponentsTagOps.setString(rootTag(stack), rootKey, key, value);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
	}

	@Override
	public void setInt(ItemStack stack, String rootKey, String key, int value) {
		CompoundTag root = ModDataComponentsTagOps.setInt(rootTag(stack), rootKey, key, value);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
	}

	@Override
	public void removeString(ItemStack stack, String rootKey, String key) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null || customData.isEmpty()) {
			return;
		}

		CompoundTag root = ModDataComponentsTagOps.remove(customData.copyTag(), rootKey, key);

		if (root.isEmpty()) {
			stack.remove(DataComponents.CUSTOM_DATA);
		} else {
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
		}
	}

	private static CompoundTag rootTag(ItemStack stack) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		return customData == null || customData.isEmpty() ? new CompoundTag() : customData.copyTag();
	}
}
