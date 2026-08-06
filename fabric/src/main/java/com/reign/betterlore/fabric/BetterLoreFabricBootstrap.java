package com.reign.betterlore.fabric;

import com.reign.betterlore.compat.CompatibilityRuntime;
import net.fabricmc.api.ModInitializer;

/** Stable Fabric entrypoint shared by exact builds and compatibility bundles. */
public final class BetterLoreFabricBootstrap implements ModInitializer {
	private static final String IMPLEMENTATION = "com.reign.betterlore.fabric.BetterLoreFabricMod";

	@Override
	public void onInitialize() {
		CompatibilityRuntime.initialize("fabric");
		CompatibilityRuntime.instantiate(IMPLEMENTATION, ModInitializer.class).onInitialize();
	}
}
