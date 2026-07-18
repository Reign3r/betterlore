package com.reign.betterlore.client.net.neoforge;

import com.reign.betterlore.client.net.BetterLoreClientNetworkingPlatform;
import com.reign.betterlore.net.ServerboundAnvilLoreUpdatePayload;
import com.reign.betterlore.net.ServerboundAnvilNameUpdatePayload;
import net.minecraft.client.Minecraft;
//? if >=1.21.7 {
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
//? } else {
import net.neoforged.neoforge.network.PacketDistributor;
//? }

/** Client-side NeoForge send bridge. */
public final class NeoForgeBetterLoreClientNetworkingPlatform implements BetterLoreClientNetworkingPlatform {
	@Override
	public void registerClientReceiver() {
		// Client receiver registration is done through RegisterClientPayloadHandlersEvent.
	}

	@Override
	public boolean canSendLoreUpdate() {
		var connection = Minecraft.getInstance().getConnection();
		return connection != null && (Minecraft.getInstance().hasSingleplayerServer()
				|| connection.hasChannel(new ServerboundAnvilLoreUpdatePayload(0, 0, "")));
	}

	@Override
	public boolean canSendNameUpdate() {
		var connection = Minecraft.getInstance().getConnection();
		return connection != null && (Minecraft.getInstance().hasSingleplayerServer()
				|| connection.hasChannel(new ServerboundAnvilNameUpdatePayload(0, 0, "")));
	}

	@Override
	public void sendLoreUpdate(ServerboundAnvilLoreUpdatePayload payload) {
		//? if >=1.21.7 {
		ClientPacketDistributor.sendToServer(payload);
		//? } else {
		PacketDistributor.sendToServer(payload);
		//? }
	}

	@Override
	public void sendNameUpdate(ServerboundAnvilNameUpdatePayload payload) {
		//? if >=1.21.7 {
		ClientPacketDistributor.sendToServer(payload);
		//? } else {
		PacketDistributor.sendToServer(payload);
		//? }
	}
}
