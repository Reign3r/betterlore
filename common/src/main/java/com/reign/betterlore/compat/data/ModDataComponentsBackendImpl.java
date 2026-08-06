package com.reign.betterlore.compat.data;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
//? if <1.21.5 {
import net.minecraft.nbt.Tag;
//? }
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

		CompoundTag root = customData.copyTag();
		CompoundTag betterLoreData = nestedBetterLoreData(root, rootKey);
		String value = nestedString(betterLoreData, key);
		return value.isEmpty() ? null : value;
	}

	@Override
	public void setString(ItemStack stack, String rootKey, String key, String value) {
		CompoundTag root = rootTag(stack);
		CompoundTag betterLoreData = nestedBetterLoreData(root, rootKey).copy();
		betterLoreData.putString(key, value);
		root.put(rootKey, betterLoreData);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
	}

	@Override
	public void removeString(ItemStack stack, String rootKey, String key) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null || customData.isEmpty()) {
			return;
		}

		CompoundTag root = customData.copyTag();
		CompoundTag betterLoreData = nestedBetterLoreData(root, rootKey).copy();
		if (betterLoreData.isEmpty()) {
			return;
		}

		betterLoreData.remove(key);
		if (betterLoreData.isEmpty()) {
			root.remove(rootKey);
		} else {
			root.put(rootKey, betterLoreData);
		}

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

	private static CompoundTag nestedBetterLoreData(CompoundTag root, String rootKey) {
		//? if >=1.21.5 {
		return root.getCompoundOrEmpty(rootKey);
		//? } else {
		return root.contains(rootKey, Tag.TAG_COMPOUND) ? root.getCompound(rootKey) : new CompoundTag();
		//? }
	}

	private static String nestedString(CompoundTag data, String key) {
		//? if >=1.21.5 {
		return data.getStringOr(key, "");
		//? } else {
		return data.contains(key, Tag.TAG_STRING) ? data.getString(key) : "";
		//? }
	}
}
