package com.reign.betterlore.client.net;

import com.reign.betterlore.net.ServerboundAnvilLoreUpdatePayload;
import com.reign.betterlore.net.ServerboundAnvilNameUpdatePayload;

public interface BetterLoreClientNetworkingPlatform {
	void registerClientReceiver();

	//? if >=1.20.6 {
	//? if <1.21 {
	/**
	 * Whether the recipe viewer needs the Lore panel's eventual bounds before
	 * the panel is first opened. Legacy Forge JEI caches exclusions that early.
	 */
	default boolean requiresStaticRecipeViewerPanelReservation() {
		return false;
	}
	//? }
	//? }

	boolean canSendLoreUpdate();

	boolean canSendNameUpdate();

	void sendLoreUpdate(ServerboundAnvilLoreUpdatePayload payload);

	void sendNameUpdate(ServerboundAnvilNameUpdatePayload payload);
}
