package com.reign.betterlore.client;

import com.reign.betterlore.client.net.ClientAnvilLoreNetworking;

public final class AnvilLoreClient {
	private AnvilLoreClient() {
	}

	public static void initClient() {
		ClientAnvilLoreNetworking.registerClientReceiver();
	}
}
