package com.reign.betterlore.fabric;

import com.reign.betterlore.AnvilLoreMod;
import com.reign.betterlore.net.AnvilLoreNetworking;
import com.reign.betterlore.net.fabric.FabricBetterLoreNetworkingPlatform;
import net.fabricmc.api.ModInitializer;

public final class BetterLoreFabricMod implements ModInitializer {
	@Override
	public void onInitialize() {
		AnvilLoreNetworking.installPlatform(new FabricBetterLoreNetworkingPlatform());
		AnvilLoreMod.init();
	}
}
