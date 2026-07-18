package com.reign.betterlore.net.fabric;

import com.reign.betterlore.net.AnvilLoreNetworking;
import com.reign.betterlore.net.BetterLoreNetworkingPlatform;
import com.reign.betterlore.net.ClientboundAnvilLoreStatePayload;
import com.reign.betterlore.net.ServerboundAnvilLoreUpdatePayload;
import com.reign.betterlore.net.ServerboundAnvilNameUpdatePayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class FabricBetterLoreNetworkingPlatform implements BetterLoreNetworkingPlatform {
	@Override
	public void registerPayloads() {
		// Fabric renamed the directional registries in the 26.1 networking API.
		//? if >=26.1 {
		PayloadTypeRegistry.serverboundPlay().register(ServerboundAnvilLoreUpdatePayload.TYPE, ServerboundAnvilLoreUpdatePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ServerboundAnvilNameUpdatePayload.TYPE, ServerboundAnvilNameUpdatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundAnvilLoreStatePayload.TYPE, ClientboundAnvilLoreStatePayload.CODEC);
		//? } else {
		PayloadTypeRegistry.playC2S().register(ServerboundAnvilLoreUpdatePayload.TYPE, ServerboundAnvilLoreUpdatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ServerboundAnvilNameUpdatePayload.TYPE, ServerboundAnvilNameUpdatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ClientboundAnvilLoreStatePayload.TYPE, ClientboundAnvilLoreStatePayload.CODEC);
		//? }
	}

	@Override
	public void registerServerReceiver() {
		// Fabric invokes object-payload play handlers on the logical server thread.
		ServerPlayNetworking.registerGlobalReceiver(ServerboundAnvilLoreUpdatePayload.TYPE,
				(payload, context) -> AnvilLoreNetworking.handleClientLoreUpdate(payload, context.player()));
		ServerPlayNetworking.registerGlobalReceiver(ServerboundAnvilNameUpdatePayload.TYPE,
				(payload, context) -> AnvilLoreNetworking.handleClientNameUpdate(payload, context.player()));
	}

	@Override
	public boolean canSendState(ServerPlayer player, ClientboundAnvilLoreStatePayload payload) {
		return ServerPlayNetworking.canSend(player, ClientboundAnvilLoreStatePayload.TYPE);
	}

	@Override
	public void sendState(ServerPlayer player, ClientboundAnvilLoreStatePayload payload) {
		if (canSendState(player, payload)) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
