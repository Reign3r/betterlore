package com.reign.betterlore.neoforge;

import com.reign.betterlore.AnvilLoreMod;
import com.reign.betterlore.client.AnvilLoreClient;
import com.reign.betterlore.client.net.ClientAnvilLoreNetworking;
import com.reign.betterlore.client.net.neoforge.NeoForgeBetterLoreClientNetworkingPlatform;
import com.reign.betterlore.net.AnvilLoreNetworking;
import com.reign.betterlore.net.neoforge.NeoForgeBetterLoreNetworkingPlatform;
import com.reign.betterlore.net.neoforge.NeoForgeClientPayloadHandlers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class BetterLoreNeoForgeMod {
	public BetterLoreNeoForgeMod(IEventBus modEventBus, Dist dist) {
		AnvilLoreNetworking.installPlatform(new NeoForgeBetterLoreNetworkingPlatform());
		AnvilLoreMod.init();
		modEventBus.addListener((RegisterPayloadHandlersEvent event) ->
				NeoForgeBetterLoreNetworkingPlatform.registerPayloadHandlers(event, dist));
		if (dist == Dist.CLIENT) {
			ClientAnvilLoreNetworking.installPlatform(new NeoForgeBetterLoreClientNetworkingPlatform());
			AnvilLoreClient.initClient();
			//? if >=1.21.8 {
			modEventBus.addListener(NeoForgeClientPayloadHandlers::registerClientPayloadHandlers);
			//? }
		}
	}
}
