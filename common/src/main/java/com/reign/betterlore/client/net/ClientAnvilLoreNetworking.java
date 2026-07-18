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
	private static BetterLoreClientNetworkingPlatform platform = loadPlatform();

	private ClientAnvilLoreNetworking() {
	}

	public static void registerClientReceiver() {
		platform.registerClientReceiver();
	}

	/**
	 * Installs the loader's client transport during its client bootstrap.
	 *
	 * <p>Service loading remains a fallback for packaged environments, but
	 * development launchers do not consistently expose their source-set service
	 * descriptors to the game class loader. An explicit registration keeps the
	 * editable anvil UI available in those environments as well.</p>
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

	//? if >=1.20.6 {
	//? if <1.21 {
	public static boolean requiresStaticRecipeViewerPanelReservation() {
		return platform.requiresStaticRecipeViewerPanelReservation();
	}
	//? }
	//? }

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
