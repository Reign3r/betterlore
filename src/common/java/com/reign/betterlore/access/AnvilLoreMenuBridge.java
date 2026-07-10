package com.reign.betterlore.access;

public interface AnvilLoreMenuBridge {
	void betterLore$handleClientLoreUpdate(int sessionId, String rawLoreMarkup);

	void betterLore$handleClientNameUpdate(int sessionId, String rawNameMarkup);
}
