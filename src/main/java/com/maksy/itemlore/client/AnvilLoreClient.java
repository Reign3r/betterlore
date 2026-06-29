package com.maksy.itemlore.client;

import com.maksy.itemlore.access.AnvilLoreScreenBridge;
import com.maksy.itemlore.net.ClientboundAnvilLoreStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;

public final class AnvilLoreClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(ClientboundAnvilLoreStatePayload.TYPE, (payload, context) ->
				context.client().execute(() -> acceptLoreState(payload, context.client().screen)));
	}

	private static void acceptLoreState(ClientboundAnvilLoreStatePayload payload, Screen screen) {
		if (!(screen instanceof AnvilScreen anvilScreen)) {
			return;
		}

		if (anvilScreen.getMenu().containerId != payload.containerId()) {
			return;
		}

		if (screen instanceof AnvilLoreScreenBridge bridge) {
			bridge.itemLore$acceptServerLoreState(payload.containerId(), payload.sessionId(), payload.safeExistingLoreMarkup(), payload.safeExistingNameMarkup());
		}
	}
}
