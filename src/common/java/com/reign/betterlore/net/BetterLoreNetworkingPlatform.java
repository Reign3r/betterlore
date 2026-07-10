package com.reign.betterlore.net;

import net.minecraft.server.level.ServerPlayer;

/** Server/common loader-specific packet wiring for Better Lore. */
public interface BetterLoreNetworkingPlatform {
	void registerPayloads();

	void registerServerReceiver();

	boolean canSendState(ServerPlayer player, ClientboundAnvilLoreStatePayload payload);

	void sendState(ServerPlayer player, ClientboundAnvilLoreStatePayload payload);
}
