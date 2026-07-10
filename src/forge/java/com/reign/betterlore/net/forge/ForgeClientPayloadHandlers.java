package com.reign.betterlore.net.forge;

import com.reign.betterlore.client.net.ClientAnvilLoreNetworking;
import com.reign.betterlore.net.ClientboundAnvilLoreStatePayload;
import net.minecraft.client.Minecraft;

public final class ForgeClientPayloadHandlers {
	private ForgeClientPayloadHandlers() {
	}

	public static void handleLoreState(ClientboundAnvilLoreStatePayload payload) {
		ClientAnvilLoreNetworking.acceptLoreState(payload, Minecraft.getInstance().screen);
	}
}
