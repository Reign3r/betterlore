package com.reign.betterlore;

import com.reign.betterlore.config.BetterLoreConfig;
import com.reign.betterlore.net.AnvilLoreNetworking;

public final class AnvilLoreMod {
	public static final String MOD_ID = "better_lore";

	private AnvilLoreMod() {
	}

	public static void init() {
		BetterLoreConfig.load();
		ModDataComponents.register();
		AnvilLoreNetworking.registerPayloads();
		AnvilLoreNetworking.registerServerReceiver();
	}
}
