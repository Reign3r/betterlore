package com.reign.betterlore.net;

import net.minecraft.server.level.ServerPlayer;

final class NoopBetterLoreNetworkingPlatform implements BetterLoreNetworkingPlatform {
	@Override
	public void registerPayloads() {
	}

	@Override
	public void registerServerReceiver() {
	}

	@Override
	public boolean canSendState(ServerPlayer player, ClientboundAnvilLoreStatePayload payload) {
		return false;
	}

	@Override
	public void sendState(ServerPlayer player, ClientboundAnvilLoreStatePayload payload) {
	}
}
