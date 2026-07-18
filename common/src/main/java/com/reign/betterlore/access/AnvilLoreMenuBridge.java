package com.reign.betterlore.access;

public interface AnvilLoreMenuBridge {
	void betterLore$setLoreEditLevelCost(int loreEditLevelCost);

	void betterLore$handleClientLoreUpdate(int sessionId, String rawLoreMarkup);

	void betterLore$handleClientNameUpdate(int sessionId, String rawNameMarkup);
}
