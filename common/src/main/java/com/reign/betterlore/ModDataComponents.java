package com.reign.betterlore;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
//? if <1.21.5 {
import net.minecraft.nbt.Tag;
//? }
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class ModDataComponents {
	private static final String ROOT_KEY = AnvilLoreMod.MOD_ID;
	private static final String RAW_LORE_MARKUP_KEY = "raw_lore_markup";
	private static final String RAW_NAME_MARKUP_KEY = "raw_name_markup";

	private ModDataComponents() {
	}

	public static void register() {
		// Intentionally no custom DataComponentType registration.
		// Custom component types are registry entries and would disconnect vanilla
		// clients during registry sync on servers. The editable source markup is
		// stored under vanilla minecraft:custom_data instead, while the visible
		// name/lore remain vanilla CUSTOM_NAME and LORE components.
	}

	public static String getRawLoreMarkup(ItemStack stack) {
		return getString(stack, RAW_LORE_MARKUP_KEY);
	}

	public static void setRawLoreMarkup(ItemStack stack, String rawMarkup) {
		setString(stack, RAW_LORE_MARKUP_KEY, rawMarkup);
	}

	public static void removeRawLoreMarkup(ItemStack stack) {
		removeString(stack, RAW_LORE_MARKUP_KEY);
	}

	public static String getRawNameMarkup(ItemStack stack) {
		return getString(stack, RAW_NAME_MARKUP_KEY);
	}

	public static void setRawNameMarkup(ItemStack stack, String rawMarkup) {
		setString(stack, RAW_NAME_MARKUP_KEY, rawMarkup);
	}

	public static void removeRawNameMarkup(ItemStack stack) {
		removeString(stack, RAW_NAME_MARKUP_KEY);
	}

	private static String getString(ItemStack stack, String key) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null || customData.isEmpty()) {
			return null;
		}

		CompoundTag root = customData.copyTag();
		CompoundTag betterLoreData = nestedBetterLoreData(root);
		String value = nestedString(betterLoreData, key);
		return value.isEmpty() ? null : value;
	}

	private static void setString(ItemStack stack, String key, String value) {
		if (value == null || value.isEmpty()) {
			removeString(stack, key);
			return;
		}

		CompoundTag root = rootTag(stack);
		CompoundTag betterLoreData = nestedBetterLoreData(root).copy();
		betterLoreData.putString(key, value);
		root.put(ROOT_KEY, betterLoreData);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
	}

	private static void removeString(ItemStack stack, String key) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null || customData.isEmpty()) {
			return;
		}

		CompoundTag root = customData.copyTag();
		CompoundTag betterLoreData = nestedBetterLoreData(root).copy();
		if (betterLoreData.isEmpty()) {
			return;
		}

		betterLoreData.remove(key);
		if (betterLoreData.isEmpty()) {
			root.remove(ROOT_KEY);
		} else {
			root.put(ROOT_KEY, betterLoreData);
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

	private static CompoundTag nestedBetterLoreData(CompoundTag root) {
		//? if >=1.21.5 {
		return root.getCompoundOrEmpty(ROOT_KEY);
		//? } else {
		return root.contains(ROOT_KEY, Tag.TAG_COMPOUND) ? root.getCompound(ROOT_KEY) : new CompoundTag();
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
