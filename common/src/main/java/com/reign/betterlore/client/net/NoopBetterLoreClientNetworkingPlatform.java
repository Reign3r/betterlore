package com.reign.betterlore.client.net;

import com.reign.betterlore.net.ServerboundAnvilLoreUpdatePayload;
import com.reign.betterlore.net.ServerboundAnvilNameUpdatePayload;

final class NoopBetterLoreClientNetworkingPlatform implements BetterLoreClientNetworkingPlatform {
	@Override
	public void registerClientReceiver() {
	}

	@Override
	public boolean canSendLoreUpdate() {
		return false;
	}

	@Override
	public boolean canSendNameUpdate() {
		return false;
	}

	@Override
	public void sendLoreUpdate(ServerboundAnvilLoreUpdatePayload payload) {
	}

	@Override
	public void sendNameUpdate(ServerboundAnvilNameUpdatePayload payload) {
	}
}
