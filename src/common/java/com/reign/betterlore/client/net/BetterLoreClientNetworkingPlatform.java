package com.reign.betterlore.client.net;

import com.reign.betterlore.net.ServerboundAnvilLoreUpdatePayload;
import com.reign.betterlore.net.ServerboundAnvilNameUpdatePayload;

public interface BetterLoreClientNetworkingPlatform {
	void registerClientReceiver();

	boolean canSendLoreUpdate();

	boolean canSendNameUpdate();

	void sendLoreUpdate(ServerboundAnvilLoreUpdatePayload payload);

	void sendNameUpdate(ServerboundAnvilNameUpdatePayload payload);
}
