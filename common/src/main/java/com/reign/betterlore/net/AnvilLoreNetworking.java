package com.reign.betterlore.net;

import com.reign.betterlore.AnvilLoreMod;
import com.reign.betterlore.access.AnvilLoreMenuBridge;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//? } else {
import net.minecraft.resources.ResourceLocation;
//? }
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;

import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

public final class AnvilLoreNetworking {
	private static final BetterLoreNetworkingPlatform PLATFORM = loadPlatform();

	private AnvilLoreNetworking() {
	}

	//? if >=1.21.11 {
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(AnvilLoreMod.MOD_ID, path);
	}
	//? } else if >=1.21 {
	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(AnvilLoreMod.MOD_ID, path);
	}
	//? } else {
	public static ResourceLocation id(String path) {
		return new ResourceLocation(AnvilLoreMod.MOD_ID, path);
	}
	//? }

	public static void registerPayloads() {
		PLATFORM.registerPayloads();
	}

	public static void registerServerReceiver() {
		PLATFORM.registerServerReceiver();
	}

	public static boolean canSendState(ServerPlayer player, ClientboundAnvilLoreStatePayload payload) {
		return PLATFORM.canSendState(player, payload);
	}

	public static void sendState(ServerPlayer player, ClientboundAnvilLoreStatePayload payload) {
		PLATFORM.sendState(player, payload);
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

	private static BetterLoreNetworkingPlatform loadPlatform() {
		try {
			ServiceLoader<BetterLoreNetworkingPlatform> loader = ServiceLoader.load(BetterLoreNetworkingPlatform.class);
			Iterator<BetterLoreNetworkingPlatform> iterator = loader.iterator();
			return iterator.hasNext() ? iterator.next() : new NoopBetterLoreNetworkingPlatform();
		} catch (ServiceConfigurationError | LinkageError error) {
			return new NoopBetterLoreNetworkingPlatform();
		}
	}
}
