package com.reign.betterlore.neoforge;

import com.reign.betterlore.AnvilLoreMod;
import com.reign.betterlore.compat.CompatibilityRuntime;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Stable NeoForge mod container shared by exact builds and release adapters. */
@Mod(AnvilLoreMod.MOD_ID)
public final class BetterLoreNeoForgeBootstrap {
	private static final Logger LOGGER = LoggerFactory.getLogger(BetterLoreNeoForgeBootstrap.class);
	private static final String IMPLEMENTATION = "com.reign.betterlore.neoforge.BetterLoreNeoForgeMod";

	public BetterLoreNeoForgeBootstrap(IEventBus modEventBus, Dist dist) {
		try {
			CompatibilityRuntime.initialize("neoforge");
			CompatibilityRuntime.instantiate(IMPLEMENTATION,
					new Class<?>[]{IEventBus.class, Dist.class}, modEventBus, dist);
		} catch (RuntimeException | Error error) {
			LOGGER.error("Better Lore NeoForge compatibility bootstrap failed", error);
			throw error;
		}
	}
}
