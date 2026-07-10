package com.reign.betterlore.client.net.forge;

import com.reign.betterlore.client.net.BetterLoreClientNetworkingPlatform;
import com.reign.betterlore.net.ServerboundAnvilLoreUpdatePayload;
import com.reign.betterlore.net.ServerboundAnvilNameUpdatePayload;
import com.reign.betterlore.net.forge.ForgeBetterLoreNetworkingPlatform;
import net.minecraft.client.Minecraft;

/** Client-side Forge send bridge. */
public final class ForgeBetterLoreClientNetworkingPlatform implements BetterLoreClientNetworkingPlatform {
	@Override
	public void registerClientReceiver() {
		// Forge SimpleChannel registers the clientbound handler with the message type.
	}

	@Override
	public boolean canSendLoreUpdate() {
		return canSendToServer();
	}

	@Override
	public boolean canSendNameUpdate() {
		return canSendToServer();
	}

	@Override
	public void sendLoreUpdate(ServerboundAnvilLoreUpdatePayload payload) {
		ForgeBetterLoreNetworkingPlatform.CHANNEL.sendToServer(payload);
	}

	@Override
	public void sendNameUpdate(ServerboundAnvilNameUpdatePayload payload) {
		ForgeBetterLoreNetworkingPlatform.CHANNEL.sendToServer(payload);
	}

	private static boolean canSendToServer() {
		var connection = Minecraft.getInstance().getConnection();
		return connection != null
				&& ForgeBetterLoreNetworkingPlatform.CHANNEL.isRemotePresent(connection.getConnection());
	}
}
