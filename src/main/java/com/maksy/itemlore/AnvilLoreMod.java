package com.maksy.itemlore;

import com.maksy.itemlore.net.AnvilLoreNetworking;
import net.fabricmc.api.ModInitializer;

public final class AnvilLoreMod implements ModInitializer {
	public static final String MOD_ID = "item_lore";

	@Override
	public void onInitialize() {
		ModDataComponents.register();
		AnvilLoreNetworking.registerPayloads();
		AnvilLoreNetworking.registerServerReceiver();
	}
}
