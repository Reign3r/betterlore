package com.reign.betterlore.neoforge;

import com.reign.betterlore.AnvilLoreMod;
import com.reign.betterlore.client.AnvilLoreClient;
import com.reign.betterlore.client.net.ClientAnvilLoreNetworking;
import com.reign.betterlore.client.net.neoforge.NeoForgeBetterLoreClientNetworkingPlatform;
import com.reign.betterlore.net.neoforge.NeoForgeBetterLoreNetworkingPlatform;
import com.reign.betterlore.net.neoforge.NeoForgeClientPayloadHandlers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(AnvilLoreMod.MOD_ID)
public final class BetterLoreNeoForgeMod {
	public BetterLoreNeoForgeMod(IEventBus modEventBus, Dist dist) {
		AnvilLoreMod.init();
		modEventBus.addListener((RegisterPayloadHandlersEvent event) ->
				NeoForgeBetterLoreNetworkingPlatform.registerPayloadHandlers(event, dist));
		//? if >=1.21.8 {
		if (dist == Dist.CLIENT) {
			ClientAnvilLoreNetworking.installPlatform(new NeoForgeBetterLoreClientNetworkingPlatform());
			AnvilLoreClient.initClient();
			modEventBus.addListener(NeoForgeClientPayloadHandlers::registerClientPayloadHandlers);
		}
		//? }
	}
}
