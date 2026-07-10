package com.reign.betterlore.net.neoforge;

import com.reign.betterlore.client.net.ClientAnvilLoreNetworking;
import com.reign.betterlore.net.ClientboundAnvilLoreStatePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class NeoForgeClientPayloadHandlers {
	private NeoForgeClientPayloadHandlers() {
	}

	public static void registerClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
		event.register(
				ClientboundAnvilLoreStatePayload.TYPE,
				NeoForgeClientPayloadHandlers::handleLoreState
		);
	}

	private static void handleLoreState(ClientboundAnvilLoreStatePayload payload, IPayloadContext context) {
		ClientAnvilLoreNetworking.acceptLoreState(payload, Minecraft.getInstance().screen);
	}
}
