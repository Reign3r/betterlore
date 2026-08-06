package com.reign.betterlore.net;

import com.reign.betterlore.access.AnvilLoreMenuBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;

public final class AnvilLoreNetworking {
	private static BetterLoreNetworkingPlatform platform = new NoopBetterLoreNetworkingPlatform();

	private AnvilLoreNetworking() {
	}

	public static Object id(String path) {
		return NetworkIdentifiers.create(path);
	}

	public static void installPlatform(BetterLoreNetworkingPlatform installedPlatform) {
		platform = installedPlatform == null ? new NoopBetterLoreNetworkingPlatform() : installedPlatform;
	}

	public static void registerPayloads() {
		platform.registerPayloads();
	}

	public static void registerServerReceiver() {
		platform.registerServerReceiver();
	}

	public static boolean canSendState(ServerPlayer player, ClientboundAnvilLoreStatePayload payload) {
		return platform.canSendState(player, payload);
	}

	public static void sendState(ServerPlayer player, ClientboundAnvilLoreStatePayload payload) {
		platform.sendState(player, payload);
	}

	public static void handleClientLoreUpdate(ServerboundAnvilLoreUpdatePayload payload, ServerPlayer player) {
		if (!(player.containerMenu instanceof AnvilMenu menu)) {
			return;
		}

		if (menu.containerId != payload.containerId()) {
			return;
		}

		if (menu instanceof AnvilLoreMenuBridge bridge) {
			bridge.betterLore$handleClientLoreUpdate(payload.sessionId(), payload.rawLoreMarkup());
		}
	}

	public static void handleClientNameUpdate(ServerboundAnvilNameUpdatePayload payload, ServerPlayer player) {
		if (!(player.containerMenu instanceof AnvilMenu menu)) {
			return;
		}

		if (menu.containerId != payload.containerId()) {
			return;
		}

		if (menu instanceof AnvilLoreMenuBridge bridge) {
			bridge.betterLore$handleClientNameUpdate(payload.sessionId(), payload.rawNameMarkup());
		}
	}

}
