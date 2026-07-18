package com.reign.betterlore.fabric;

import com.reign.betterlore.AnvilLoreMod;
import net.fabricmc.api.ModInitializer;

public final class BetterLoreFabricMod implements ModInitializer {
	@Override
	public void onInitialize() {
		AnvilLoreMod.init();
	}
}
