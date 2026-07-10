package com.reign.betterlore.client.net.fabric;

import com.reign.betterlore.client.net.BetterLoreClientNetworkingPlatform;
import com.reign.betterlore.client.net.ClientAnvilLoreNetworking;
import com.reign.betterlore.net.ClientboundAnvilLoreStatePayload;
import com.reign.betterlore.net.ServerboundAnvilLoreUpdatePayload;
import com.reign.betterlore.net.ServerboundAnvilNameUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class FabricBetterLoreClientNetworkingPlatform implements BetterLoreClientNetworkingPlatform {
	@Override
	public void registerClientReceiver() {
		ClientPlayNetworking.registerGlobalReceiver(ClientboundAnvilLoreStatePayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientAnvilLoreNetworking.acceptLoreState(payload, context.client().screen)));
	}

	@Override
	public boolean canSendLoreUpdate() {
		return ClientPlayNetworking.canSend(ServerboundAnvilLoreUpdatePayload.TYPE);
	}

	@Override
	public boolean canSendNameUpdate() {
		return ClientPlayNetworking.canSend(ServerboundAnvilNameUpdatePayload.TYPE);
	}

	@Override
	public void sendLoreUpdate(ServerboundAnvilLoreUpdatePayload payload) {
		ClientPlayNetworking.send(payload);
	}

	@Override
	public void sendNameUpdate(ServerboundAnvilNameUpdatePayload payload) {
		ClientPlayNetworking.send(payload);
	}
}
