package com.reign.betterlore;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
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
		CompoundTag betterLoreData = root.getCompoundOrEmpty(ROOT_KEY);
		String value = betterLoreData.getStringOr(key, "");
		return value.isEmpty() ? null : value;
	}

	private static void setString(ItemStack stack, String key, String value) {
		if (value == null || value.isEmpty()) {
			removeString(stack, key);
			return;
		}

		CompoundTag root = rootTag(stack);
		CompoundTag betterLoreData = root.getCompoundOrEmpty(ROOT_KEY).copy();
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
		CompoundTag betterLoreData = root.getCompoundOrEmpty(ROOT_KEY).copy();
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
}
