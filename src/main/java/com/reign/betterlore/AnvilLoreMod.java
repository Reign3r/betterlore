package com.reign.betterlore;

import com.reign.betterlore.net.AnvilLoreNetworking;
import net.fabricmc.api.ModInitializer;

public final class AnvilLoreMod implements ModInitializer {
	public static final String MOD_ID = "better_lore";

	@Override
	public void onInitialize() {
		ModDataComponents.register();
		AnvilLoreNetworking.registerPayloads();
		AnvilLoreNetworking.registerServerReceiver();
	}
}
