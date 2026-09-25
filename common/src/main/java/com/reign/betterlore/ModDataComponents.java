package com.reign.betterlore;

import com.reign.betterlore.compat.CompatibilityRuntime;
import com.reign.betterlore.compat.data.ModDataComponentsBackend;
import net.minecraft.world.item.ItemStack;

public final class ModDataComponents {
	private static final String BACKEND_CLASS_NAME =
			"com.reign.betterlore.compat.data.ModDataComponentsBackendImpl";
	private static final String ROOT_KEY = AnvilLoreMod.MOD_ID;
	private static final String RAW_LORE_MARKUP_KEY = "raw_lore_markup";
	private static final String OWNED_LORE_VERSION_KEY = "owned_lore_version";
	private static final int OWNED_LORE_VERSION = 1;
	private static final String LEGACY_SERVER_API_OWNED_LORE_VERSION_KEY = "server_api_owned_lore_version";
	private static final String LEGACY_SERVER_API_OWNED_LORE_VERSION = "1";
	private static final String RAW_NAME_MARKUP_KEY = "raw_name_markup";
	private static final String OWNED_NAME_VERSION_KEY = "owned_name_version";
	private static final int OWNED_NAME_VERSION = 1;

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

	/** Marks source owned by Better Lore without registering a client-visible component type. */
	public static void setOwnedLoreMarkup(ItemStack stack, String rawMarkup) {
		if (rawMarkup == null || rawMarkup.isEmpty()) {
			removeRawLoreMarkup(stack);
			return;
		}
		setString(stack, RAW_LORE_MARKUP_KEY, rawMarkup);
		backend().setInt(stack, ROOT_KEY, OWNED_LORE_VERSION_KEY, OWNED_LORE_VERSION);
		removeString(stack, LEGACY_SERVER_API_OWNED_LORE_VERSION_KEY);
	}

	public static boolean hasCurrentLoreOwnership(ItemStack stack) {
		return backend().getInt(stack, ROOT_KEY, OWNED_LORE_VERSION_KEY, 0) == OWNED_LORE_VERSION
				|| LEGACY_SERVER_API_OWNED_LORE_VERSION.equals(
						getString(stack, LEGACY_SERVER_API_OWNED_LORE_VERSION_KEY)
				);
	}

	/** Compatibility alias for integrations compiled against the earlier server-API preview. */
	@Deprecated(forRemoval = false)
	public static void setServerApiOwnedLoreMarkup(ItemStack stack, String rawMarkup) {
		setOwnedLoreMarkup(stack, rawMarkup);
	}

	/** Compatibility alias for integrations compiled against the earlier server-API preview. */
	@Deprecated(forRemoval = false)
	public static boolean hasCurrentServerApiLoreOwnership(ItemStack stack) {
		return hasCurrentLoreOwnership(stack);
	}

	public static void removeRawLoreMarkup(ItemStack stack) {
		removeString(stack, RAW_LORE_MARKUP_KEY);
		removeString(stack, OWNED_LORE_VERSION_KEY);
		removeString(stack, LEGACY_SERVER_API_OWNED_LORE_VERSION_KEY);
	}

	public static String getRawNameMarkup(ItemStack stack) {
		return getString(stack, RAW_NAME_MARKUP_KEY);
	}

	public static void setRawNameMarkup(ItemStack stack, String rawMarkup) {
		setString(stack, RAW_NAME_MARKUP_KEY, rawMarkup);
	}

	/** Marks validated name source produced by the current editor/migration path. */
	public static void setOwnedNameMarkup(ItemStack stack, String rawMarkup) {
		if (rawMarkup == null || rawMarkup.isEmpty()) {
			removeRawNameMarkup(stack);
			return;
		}
		setString(stack, RAW_NAME_MARKUP_KEY, rawMarkup);
		backend().setInt(stack, ROOT_KEY, OWNED_NAME_VERSION_KEY, OWNED_NAME_VERSION);
	}

	public static boolean hasCurrentNameOwnership(ItemStack stack) {
		return backend().getInt(stack, ROOT_KEY, OWNED_NAME_VERSION_KEY, 0) == OWNED_NAME_VERSION;
	}

	public static void removeRawNameMarkup(ItemStack stack) {
		removeString(stack, RAW_NAME_MARKUP_KEY);
		removeString(stack, OWNED_NAME_VERSION_KEY);
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
