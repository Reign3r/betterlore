package com.reign.betterlore.net.neoforge;

import com.reign.betterlore.client.net.ClientAnvilLoreNetworking;
import com.reign.betterlore.net.ClientboundAnvilLoreStatePayload;
import net.minecraft.client.Minecraft;
//? if >=1.21.8 {
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
//? }
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class NeoForgeClientPayloadHandlers {
	private NeoForgeClientPayloadHandlers() {
	}

	//? if >=1.21.8 {
	public static void registerClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
		event.register(
				ClientboundAnvilLoreStatePayload.TYPE,
				NeoForgeClientPayloadHandlers::handleLoreState
		);
	}
	//? }

	static void handleLoreState(ClientboundAnvilLoreStatePayload payload, IPayloadContext context) {
		ClientAnvilLoreNetworking.acceptLoreState(payload,
				//? if >=26.2 {
				Minecraft.getInstance().gui.screen()
				//? } else {
				Minecraft.getInstance().screen
				//? }
		);
	}
}
