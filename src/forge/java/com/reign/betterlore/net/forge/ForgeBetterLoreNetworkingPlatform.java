package com.reign.betterlore.net.forge;

import com.reign.betterlore.net.AnvilLoreNetworking;
import com.reign.betterlore.net.BetterLoreNetworkingPlatform;
import com.reign.betterlore.net.ClientboundAnvilLoreStatePayload;
import com.reign.betterlore.net.ServerboundAnvilLoreUpdatePayload;
import com.reign.betterlore.net.ServerboundAnvilNameUpdatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * Forge packet bridge for Better Lore.
 *
 * <p>This source set deliberately uses Forge's SimpleChannel API while the
 * shared mod code only knows about {@link AnvilLoreNetworking}. The channel is
 * optional so servers can still allow clients without Better Lore installed.</p>
 */
public final class ForgeBetterLoreNetworkingPlatform implements BetterLoreNetworkingPlatform {
	private static final String PROTOCOL_VERSION = "1";
	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
			AnvilLoreNetworking.id("main"),
			() -> PROTOCOL_VERSION,
			ForgeBetterLoreNetworkingPlatform::acceptRemoteVersion,
			ForgeBetterLoreNetworkingPlatform::acceptRemoteVersion
	);

	private static boolean registered;

	public static void registerMessages() {
		if (registered) {
			return;
		}

		int id = 0;
		CHANNEL.registerMessage(
				id++,
				ServerboundAnvilLoreUpdatePayload.class,
				ServerboundAnvilLoreUpdatePayload::write,
				ServerboundAnvilLoreUpdatePayload::read,
				ForgeBetterLoreNetworkingPlatform::handleLoreUpdate
		);
		CHANNEL.registerMessage(
				id++,
				ServerboundAnvilNameUpdatePayload.class,
				ServerboundAnvilNameUpdatePayload::write,
				ServerboundAnvilNameUpdatePayload::read,
				ForgeBetterLoreNetworkingPlatform::handleNameUpdate
		);
		CHANNEL.registerMessage(
				id++,
				ClientboundAnvilLoreStatePayload.class,
				ClientboundAnvilLoreStatePayload::write,
				ClientboundAnvilLoreStatePayload::read,
				ForgeBetterLoreNetworkingPlatform::handleLoreState
		);
		registered = true;
	}

	private static boolean acceptRemoteVersion(String remoteVersion) {
		return PROTOCOL_VERSION.equals(remoteVersion)
				|| NetworkRegistry.ABSENT.equals(remoteVersion)
				|| NetworkRegistry.ACCEPTVANILLA.equals(remoteVersion);
	}

	private static void handleLoreUpdate(ServerboundAnvilLoreUpdatePayload payload, Supplier<net.minecraftforge.network.NetworkEvent.Context> contextSupplier) {
		var context = contextSupplier.get();
		context.enqueueWork(() -> {
			ServerPlayer sender = context.getSender();
			if (sender != null) {
				AnvilLoreNetworking.handleClientLoreUpdate(payload, sender);
			}
		});
		context.setPacketHandled(true);
	}

	private static void handleNameUpdate(ServerboundAnvilNameUpdatePayload payload, Supplier<net.minecraftforge.network.NetworkEvent.Context> contextSupplier) {
		var context = contextSupplier.get();
		context.enqueueWork(() -> {
			ServerPlayer sender = context.getSender();
			if (sender != null) {
				AnvilLoreNetworking.handleClientNameUpdate(payload, sender);
			}
		});
		context.setPacketHandled(true);
	}

	private static void handleLoreState(ClientboundAnvilLoreStatePayload payload, Supplier<net.minecraftforge.network.NetworkEvent.Context> contextSupplier) {
		var context = contextSupplier.get();
		context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
				Dist.CLIENT,
				() -> () -> ForgeClientPayloadHandlers.handleLoreState(payload)
		));
		context.setPacketHandled(true);
	}

	@Override
	public void registerPayloads() {
		registerMessages();
	}

	@Override
	public void registerServerReceiver() {
		// Forge SimpleChannel registers handlers together with message types.
	}

	@Override
	public boolean canSendState(ServerPlayer player, ClientboundAnvilLoreStatePayload payload) {
		return CHANNEL.isRemotePresent(player.connection.getConnection());
	}

	@Override
	public void sendState(ServerPlayer player, ClientboundAnvilLoreStatePayload payload) {
		CHANNEL.send(PacketDistributor.PLAYER.with(player), payload);
	}

}
