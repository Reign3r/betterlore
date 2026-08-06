package com.reign.betterlore.client.net;

import com.reign.betterlore.access.AnvilLoreScreenBridge;
import com.reign.betterlore.net.ClientboundAnvilLoreStatePayload;
import com.reign.betterlore.net.ServerboundAnvilLoreUpdatePayload;
import com.reign.betterlore.net.ServerboundAnvilNameUpdatePayload;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;

public final class ClientAnvilLoreNetworking {
	private static BetterLoreClientNetworkingPlatform platform = new NoopBetterLoreClientNetworkingPlatform();

	private ClientAnvilLoreNetworking() {
	}

	public static void registerClientReceiver() {
		platform.registerClientReceiver();
	}

	/**
	 * Installs the loader's client transport during its client bootstrap.
	 *
	 * <p>The compatibility bootstrap installs exactly one implementation. This
	 * avoids probing inactive version adapters through {@link java.util.ServiceLoader}.</p>
	 */
	public static void installPlatform(BetterLoreClientNetworkingPlatform installedPlatform) {
		platform = installedPlatform == null ? new NoopBetterLoreClientNetworkingPlatform() : installedPlatform;
	}

	public static boolean canSendLoreUpdate() {
		return platform.canSendLoreUpdate();
	}

	public static boolean canSendNameUpdate() {
		return platform.canSendNameUpdate();
	}

	public static boolean requiresStaticRecipeViewerPanelReservation() {
		return platform.requiresStaticRecipeViewerPanelReservation();
	}

	public static void sendLoreUpdate(ServerboundAnvilLoreUpdatePayload payload) {
		platform.sendLoreUpdate(payload);
	}

	public static void sendNameUpdate(ServerboundAnvilNameUpdatePayload payload) {
		platform.sendNameUpdate(payload);
	}

	public static void acceptLoreState(ClientboundAnvilLoreStatePayload payload, Screen screen) {
		if (!(screen instanceof AnvilScreen anvilScreen)) {
			return;
		}

		if (anvilScreen.getMenu().containerId != payload.containerId()) {
			return;
		}

		if (screen instanceof AnvilLoreScreenBridge bridge) {
			bridge.betterLore$acceptServerLoreState(payload.containerId(), payload.sessionId(), payload.safeExistingLoreMarkup(), payload.safeExistingNameMarkup(), payload.loreEditLevelCost());
		}
	}

}
