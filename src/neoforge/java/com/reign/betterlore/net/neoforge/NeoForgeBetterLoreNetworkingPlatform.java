package com.reign.betterlore.net.neoforge;

import com.reign.betterlore.AnvilLoreMod;
import com.reign.betterlore.net.AnvilLoreNetworking;
import com.reign.betterlore.net.BetterLoreNetworkingPlatform;
import com.reign.betterlore.net.ClientboundAnvilLoreStatePayload;
import com.reign.betterlore.net.ServerboundAnvilLoreUpdatePayload;
import com.reign.betterlore.net.ServerboundAnvilNameUpdatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge packet bridge for Better Lore.
 *
 * <p>This file lives in the NeoForge source set so Fabric builds never see
 * NeoForge classes. The shared anvil/menu logic continues to call only
 * {@link AnvilLoreNetworking}.</p>
 */
public final class NeoForgeBetterLoreNetworkingPlatform implements BetterLoreNetworkingPlatform {
	private static final String NETWORK_VERSION = "1";

	public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar(AnvilLoreMod.MOD_ID)
				.versioned(NETWORK_VERSION)
				.optional();

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
		registrar.playToClient(
				ClientboundAnvilLoreStatePayload.TYPE,
				ClientboundAnvilLoreStatePayload.CODEC
		);
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

	@Override
	public void registerPayloads() {
		// Registration is done through RegisterPayloadHandlersEvent on the mod event bus.
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
		PacketDistributor.sendToPlayer(player, payload);
	}
}
