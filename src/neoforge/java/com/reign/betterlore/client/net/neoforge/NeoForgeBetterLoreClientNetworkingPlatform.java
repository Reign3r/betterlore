package com.reign.betterlore.client.net.neoforge;

import com.reign.betterlore.client.net.BetterLoreClientNetworkingPlatform;
import com.reign.betterlore.net.ServerboundAnvilLoreUpdatePayload;
import com.reign.betterlore.net.ServerboundAnvilNameUpdatePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Client-side NeoForge send bridge. */
public final class NeoForgeBetterLoreClientNetworkingPlatform implements BetterLoreClientNetworkingPlatform {
	@Override
	public void registerClientReceiver() {
		// Client receiver registration is done through RegisterClientPayloadHandlersEvent.
	}

	@Override
	public boolean canSendLoreUpdate() {
		var connection = Minecraft.getInstance().getConnection();
		return connection != null && connection.hasChannel(new ServerboundAnvilLoreUpdatePayload(0, 0, ""));
	}

	@Override
	public boolean canSendNameUpdate() {
		var connection = Minecraft.getInstance().getConnection();
		return connection != null && connection.hasChannel(new ServerboundAnvilNameUpdatePayload(0, 0, ""));
	}

	@Override
	public void sendLoreUpdate(ServerboundAnvilLoreUpdatePayload payload) {
		ClientPacketDistributor.sendToServer(payload);
	}

	@Override
	public void sendNameUpdate(ServerboundAnvilNameUpdatePayload payload) {
		ClientPacketDistributor.sendToServer(payload);
	}
}
