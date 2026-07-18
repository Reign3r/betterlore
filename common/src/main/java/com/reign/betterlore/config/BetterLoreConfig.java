package com.reign.betterlore.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Loader-independent server configuration.
 *
 * <p>All supported loaders use the game directory as their working directory,
 * which keeps this small properties file in the normal {@code config} folder
 * without depending on a loader-specific configuration API.</p>
 */
public final class BetterLoreConfig {
	public static final int MIN_LORE_EDIT_LEVEL_COST = 0;
	public static final int MAX_LORE_EDIT_LEVEL_COST = 255;
	public static final int DEFAULT_LORE_EDIT_LEVEL_COST = 1;

	private static final String LORE_EDIT_LEVEL_COST_KEY = "lore_edit_level_cost";
	private static final Path CONFIG_PATH = Path.of("config", "better_lore.properties");

	private static volatile int loreEditLevelCost = DEFAULT_LORE_EDIT_LEVEL_COST;

	private BetterLoreConfig() {
	}

	public static synchronized void load() {
		Properties properties = new Properties();
		if (Files.isRegularFile(CONFIG_PATH)) {
			try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
				properties.load(reader);
			} catch (IOException ignored) {
				// Keep the safe default if the config cannot be read.
			}
		}

		int configuredCost = parseLevelCost(properties.getProperty(LORE_EDIT_LEVEL_COST_KEY));
		loreEditLevelCost = configuredCost;
		properties.setProperty(LORE_EDIT_LEVEL_COST_KEY, Integer.toString(configuredCost));

		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
				properties.store(writer, "Better Lore server configuration (valid lore edit cost: 0-255 levels)");
			}
		} catch (IOException ignored) {
			// A read-only game directory must not prevent the mod from starting.
		}
	}

	public static int loreEditLevelCost() {
		return loreEditLevelCost;
	}

	static int parseLevelCost(String rawValue) {
		if (rawValue == null) {
			return DEFAULT_LORE_EDIT_LEVEL_COST;
		}

		try {
			return clampLoreEditLevelCost(Integer.parseInt(rawValue.trim()));
		} catch (NumberFormatException ignored) {
			return DEFAULT_LORE_EDIT_LEVEL_COST;
		}
	}

	public static int clampLoreEditLevelCost(int cost) {
		return Math.max(MIN_LORE_EDIT_LEVEL_COST, Math.min(MAX_LORE_EDIT_LEVEL_COST, cost));
	}
}
