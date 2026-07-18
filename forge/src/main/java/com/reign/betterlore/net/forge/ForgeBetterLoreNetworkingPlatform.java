package com.reign.betterlore.net.forge;

import com.reign.betterlore.net.AnvilLoreNetworking;
import com.reign.betterlore.net.BetterLoreNetworkingPlatform;
import com.reign.betterlore.net.ClientboundAnvilLoreStatePayload;
import com.reign.betterlore.net.ServerboundAnvilLoreUpdatePayload;
import com.reign.betterlore.net.ServerboundAnvilNameUpdatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/**
 * Loader-native Forge transport.
 *
 * <p>The channel is explicitly optional on both ends. A Forge server therefore
 * accepts vanilla and modded clients that do not advertise Better Lore, and the
 * server checks channel presence before sending the UI-state payload.</p>
 */
public final class ForgeBetterLoreNetworkingPlatform implements BetterLoreNetworkingPlatform {
	private static final int NETWORK_PROTOCOL = 1;
	public static final SimpleChannel CHANNEL = ChannelBuilder.named(AnvilLoreNetworking.id("main"))
			.networkProtocolVersion(NETWORK_PROTOCOL)
			.optional()
			.simpleChannel();

	private static boolean registered;

	public static synchronized void registerMessages() {
		if (registered) {
			return;
		}

		int id = 0;
		CHANNEL.messageBuilder(ServerboundAnvilLoreUpdatePayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
				.encoder(ServerboundAnvilLoreUpdatePayload::write)
				.decoder(ServerboundAnvilLoreUpdatePayload::read)
				.consumerMainThread((payload, context) -> {
					ServerPlayer sender = context.getSender();
					if (sender != null) {
						AnvilLoreNetworking.handleClientLoreUpdate(payload, sender);
					}
				})
				.add();

		CHANNEL.messageBuilder(ServerboundAnvilNameUpdatePayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
				.encoder(ServerboundAnvilNameUpdatePayload::write)
				.decoder(ServerboundAnvilNameUpdatePayload::read)
				.consumerMainThread((payload, context) -> {
					ServerPlayer sender = context.getSender();
					if (sender != null) {
						AnvilLoreNetworking.handleClientNameUpdate(payload, sender);
					}
				})
				.add();

		CHANNEL.messageBuilder(ClientboundAnvilLoreStatePayload.class, id, NetworkDirection.PLAY_TO_CLIENT)
				.encoder(ClientboundAnvilLoreStatePayload::write)
				.decoder(ClientboundAnvilLoreStatePayload::read)
				.consumerMainThread((payload, context) -> {
					if (FMLEnvironment.dist == Dist.CLIENT) {
						ForgeClientPayloadHandlers.handleLoreState(payload);
					}
				})
				.add();

		CHANNEL.build();
		registered = true;
	}

	@Override
	public void registerPayloads() {
		registerMessages();
	}

	@Override
	public void registerServerReceiver() {
		// Message handlers are installed together with their codecs above.
	}

	@Override
	public boolean canSendState(ServerPlayer player, ClientboundAnvilLoreStatePayload payload) {
		return CHANNEL.isRemotePresent(player.connection.getConnection());
	}

	@Override
	public void sendState(ServerPlayer player, ClientboundAnvilLoreStatePayload payload) {
		if (canSendState(player, payload)) {
			CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
		}
	}
}
