package com.reign.betterlore.net.neoforge;

import com.reign.betterlore.net.AnvilLoreNetworking;
import com.reign.betterlore.net.BetterLoreNetworkingPlatform;
import com.reign.betterlore.net.ClientboundAnvilLoreStatePayload;
import com.reign.betterlore.net.ServerboundAnvilLoreUpdatePayload;
import com.reign.betterlore.net.ServerboundAnvilNameUpdatePayload;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Loader-native NeoForge transport shared by every supported component-era
 * Minecraft target.
 *
 * <p>All three payloads are optional. Missing Better Lore payloads therefore do
 * not prevent an unmodded client from joining a Better Lore server.</p>
 */
public final class NeoForgeBetterLoreNetworkingPlatform implements BetterLoreNetworkingPlatform {
	private static final String NETWORK_VERSION = "1";

	public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event, Dist dist) {
		PayloadRegistrar registrar = event.registrar(NETWORK_VERSION).optional();

		registrar.playToServer(
				ServerboundAnvilLoreUpdatePayload.TYPE,
				ServerboundAnvilLoreUpdatePayload.CODEC,
				NeoForgeBetterLoreNetworkingPlatform::handleLoreUpdate
		);
		registrar.playToServer(
				ServerboundAnvilNameUpdatePayload.TYPE,
				ServerboundAnvilNameUpdatePayload.CODEC,
				NeoForgeBetterLoreNetworkingPlatform::handleNameUpdate
		);
		//? if >=1.21.8 {
		registrar.playToClient(
				ClientboundAnvilLoreStatePayload.TYPE,
				ClientboundAnvilLoreStatePayload.CODEC
		);
		//? } else {
		registrar.playToClient(
				ClientboundAnvilLoreStatePayload.TYPE,
				ClientboundAnvilLoreStatePayload.CODEC,
				dist == Dist.CLIENT
						? NeoForgeClientPayloadHandlers::handleLoreState
						: NeoForgeBetterLoreNetworkingPlatform::ignoreClientLoreState
		);
		//? }
	}

	private static void handleLoreUpdate(ServerboundAnvilLoreUpdatePayload payload, IPayloadContext context) {
		if (context.player() instanceof ServerPlayer player) {
			AnvilLoreNetworking.handleClientLoreUpdate(payload, player);
		}
	}

	private static void handleNameUpdate(ServerboundAnvilNameUpdatePayload payload, IPayloadContext context) {
		if (context.player() instanceof ServerPlayer player) {
			AnvilLoreNetworking.handleClientNameUpdate(payload, player);
		}
	}

	private static void ignoreClientLoreState(ClientboundAnvilLoreStatePayload payload, IPayloadContext context) {
		// Dedicated servers must register the payload codec but never load client classes.
	}

	@Override
	public void registerPayloads() {
		// Registration is performed by RegisterPayloadHandlersEvent.
	}

	@Override
	public void registerServerReceiver() {
		// Server receivers are registered with the payload registrar above.
	}

	@Override
	public boolean canSendState(ServerPlayer player, ClientboundAnvilLoreStatePayload payload) {
		return player.connection.hasChannel(payload);
	}

	@Override
	public void sendState(ServerPlayer player, ClientboundAnvilLoreStatePayload payload) {
		if (canSendState(player, payload)) {
			// Use Minecraft's payload packet directly. This remains stable across
			// NeoForge's PacketDistributor changes between 20.5 and 26.x.
			player.connection.send(new ClientboundCustomPayloadPacket(payload));
		}
	}
}
