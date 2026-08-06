package com.reign.betterlore.fabric;

import com.reign.betterlore.compat.CompatibilityRuntime;
import net.fabricmc.api.ClientModInitializer;

/** Stable Fabric client entrypoint shared by exact builds and bundles. */
public final class BetterLoreFabricClientBootstrap implements ClientModInitializer {
	private static final String IMPLEMENTATION = "com.reign.betterlore.fabric.BetterLoreFabricClientMod";

	@Override
	public void onInitializeClient() {
		CompatibilityRuntime.initialize("fabric");
		CompatibilityRuntime.instantiate(IMPLEMENTATION, ClientModInitializer.class).onInitializeClient();
	}
}
