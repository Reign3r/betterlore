package com.reign.betterlore.client.net;

import com.reign.betterlore.access.AnvilLoreScreenBridge;
import com.reign.betterlore.net.ClientboundAnvilLoreStatePayload;
import com.reign.betterlore.net.ServerboundAnvilLoreUpdatePayload;
import com.reign.betterlore.net.ServerboundAnvilNameUpdatePayload;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;

import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

public final class ClientAnvilLoreNetworking {
	private static final BetterLoreClientNetworkingPlatform PLATFORM = loadPlatform();

	private ClientAnvilLoreNetworking() {
	}

	public static void registerClientReceiver() {
		PLATFORM.registerClientReceiver();
	}

	public static boolean canSendLoreUpdate() {
		return PLATFORM.canSendLoreUpdate();
	}

	public static boolean canSendNameUpdate() {
		return PLATFORM.canSendNameUpdate();
	}

	public static void sendLoreUpdate(ServerboundAnvilLoreUpdatePayload payload) {
		PLATFORM.sendLoreUpdate(payload);
	}

	public static void sendNameUpdate(ServerboundAnvilNameUpdatePayload payload) {
		PLATFORM.sendNameUpdate(payload);
	}

	public static void acceptLoreState(ClientboundAnvilLoreStatePayload payload, Screen screen) {
		if (!(screen instanceof AnvilScreen anvilScreen)) {
			return;
		}

		if (anvilScreen.getMenu().containerId != payload.containerId()) {
			return;
		}

		if (screen instanceof AnvilLoreScreenBridge bridge) {
			bridge.betterLore$acceptServerLoreState(payload.containerId(), payload.sessionId(), payload.safeExistingLoreMarkup(), payload.safeExistingNameMarkup());
		}
	}

	private static BetterLoreClientNetworkingPlatform loadPlatform() {
		try {
			ServiceLoader<BetterLoreClientNetworkingPlatform> loader = ServiceLoader.load(BetterLoreClientNetworkingPlatform.class);
			Iterator<BetterLoreClientNetworkingPlatform> iterator = loader.iterator();
			return iterator.hasNext() ? iterator.next() : new NoopBetterLoreClientNetworkingPlatform();
		} catch (ServiceConfigurationError | LinkageError error) {
			return new NoopBetterLoreClientNetworkingPlatform();
		}
	}
}
