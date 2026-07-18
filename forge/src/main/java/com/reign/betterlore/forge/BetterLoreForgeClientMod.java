package com.reign.betterlore.forge;

import com.reign.betterlore.client.AnvilLoreClient;
import com.reign.betterlore.client.net.ClientAnvilLoreNetworking;
import com.reign.betterlore.client.net.forge.ForgeBetterLoreClientNetworkingPlatform;

/** Registers Better Lore's client receiver without loading client classes on a server. */
public final class BetterLoreForgeClientMod {
	private BetterLoreForgeClientMod() {
	}

	public static void initialize() {
		ClientAnvilLoreNetworking.installPlatform(new ForgeBetterLoreClientNetworkingPlatform());
		AnvilLoreClient.initClient();
	}
}
