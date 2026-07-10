package com.reign.betterlore.fabric;

import com.reign.betterlore.client.AnvilLoreClient;
import net.fabricmc.api.ClientModInitializer;

public final class BetterLoreFabricClientMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		AnvilLoreClient.initClient();
	}
}
