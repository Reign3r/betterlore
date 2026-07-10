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
		PayloadTypeRegistry.serverboundPlay().register(ServerboundAnvilLoreUpdatePayload.TYPE, ServerboundAnvilLoreUpdatePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ServerboundAnvilNameUpdatePayload.TYPE, ServerboundAnvilNameUpdatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundAnvilLoreStatePayload.TYPE, ClientboundAnvilLoreStatePayload.CODEC);
	}

	@Override
	public void registerServerReceiver() {
		ServerPlayNetworking.registerGlobalReceiver(ServerboundAnvilLoreUpdatePayload.TYPE, (payload, context) ->
				context.server().execute(() -> AnvilLoreNetworking.handleClientLoreUpdate(payload, context.player())));
		ServerPlayNetworking.registerGlobalReceiver(ServerboundAnvilNameUpdatePayload.TYPE, (payload, context) ->
				context.server().execute(() -> AnvilLoreNetworking.handleClientNameUpdate(payload, context.player())));
	}

	@Override
	public boolean canSendState(ServerPlayer player, ClientboundAnvilLoreStatePayload payload) {
		return ServerPlayNetworking.canSend(player, ClientboundAnvilLoreStatePayload.TYPE);
	}

	@Override
	public void sendState(ServerPlayer player, ClientboundAnvilLoreStatePayload payload) {
		ServerPlayNetworking.send(player, payload);
	}
}
