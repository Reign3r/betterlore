package com.reign.betterlore.fabric;

import com.reign.betterlore.client.AnvilLoreClient;
import com.reign.betterlore.client.net.ClientAnvilLoreNetworking;
import com.reign.betterlore.client.net.fabric.FabricBetterLoreClientNetworkingPlatform;
import net.fabricmc.api.ClientModInitializer;

public final class BetterLoreFabricClientMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientAnvilLoreNetworking.installPlatform(new FabricBetterLoreClientNetworkingPlatform());
		AnvilLoreClient.initClient();
	}
}
