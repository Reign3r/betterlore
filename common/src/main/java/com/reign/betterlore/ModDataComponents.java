package com.reign.betterlore;

import com.reign.betterlore.compat.CompatibilityRuntime;
import com.reign.betterlore.compat.data.ModDataComponentsBackend;
import net.minecraft.world.item.ItemStack;

public final class ModDataComponents {
	private static final String BACKEND_CLASS_NAME =
			"com.reign.betterlore.compat.data.ModDataComponentsBackendImpl";
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
		return backend().getString(stack, ROOT_KEY, key);
	}

	private static void setString(ItemStack stack, String key, String value) {
		if (value == null || value.isEmpty()) {
			removeString(stack, key);
			return;
		}

		backend().setString(stack, ROOT_KEY, key, value);
	}

	private static void removeString(ItemStack stack, String key) {
		backend().removeString(stack, ROOT_KEY, key);
	}

	private static ModDataComponentsBackend backend() {
		return BackendHolder.INSTANCE;
	}

	private static final class BackendHolder {
		private static final ModDataComponentsBackend INSTANCE = CompatibilityRuntime.instantiate(
				BACKEND_CLASS_NAME,
				ModDataComponentsBackend.class
		);
	}
}
