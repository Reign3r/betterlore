package com.maksy.itemlore.net;

import com.maksy.itemlore.AnvilLoreMod;
import com.maksy.itemlore.access.AnvilLoreMenuBridge;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;

public final class AnvilLoreNetworking {
	private AnvilLoreNetworking() {
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(AnvilLoreMod.MOD_ID, path);
	}

	public static void registerPayloads() {
		PayloadTypeRegistry.serverboundPlay().register(ServerboundAnvilLoreUpdatePayload.TYPE, ServerboundAnvilLoreUpdatePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ServerboundAnvilNameUpdatePayload.TYPE, ServerboundAnvilNameUpdatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundAnvilLoreStatePayload.TYPE, ClientboundAnvilLoreStatePayload.CODEC);
	}

	public static void registerServerReceiver() {
		ServerPlayNetworking.registerGlobalReceiver(ServerboundAnvilLoreUpdatePayload.TYPE, (payload, context) ->
				context.server().execute(() -> handleClientLoreUpdate(payload, context.player())));
		ServerPlayNetworking.registerGlobalReceiver(ServerboundAnvilNameUpdatePayload.TYPE, (payload, context) ->
				context.server().execute(() -> handleClientNameUpdate(payload, context.player())));
	}

	private static void handleClientLoreUpdate(ServerboundAnvilLoreUpdatePayload payload, ServerPlayer player) {
		if (!(player.containerMenu instanceof AnvilMenu menu)) {
			return;
		}

		if (menu.containerId != payload.containerId()) {
			return;
		}

		if (menu instanceof AnvilLoreMenuBridge bridge) {
			bridge.itemLore$handleClientLoreUpdate(payload.sessionId(), payload.rawLoreMarkup());
		}
	}

	private static void handleClientNameUpdate(ServerboundAnvilNameUpdatePayload payload, ServerPlayer player) {
		if (!(player.containerMenu instanceof AnvilMenu menu)) {
			return;
		}

		if (menu.containerId != payload.containerId()) {
			return;
		}

		if (menu instanceof AnvilLoreMenuBridge bridge) {
			bridge.itemLore$handleClientNameUpdate(payload.sessionId(), payload.rawNameMarkup());
		}
	}
}
