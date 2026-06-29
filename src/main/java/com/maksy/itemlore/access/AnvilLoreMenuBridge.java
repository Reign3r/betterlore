package com.maksy.itemlore.access;

public interface AnvilLoreMenuBridge {
	void itemLore$handleClientLoreUpdate(int sessionId, String rawLoreMarkup);

	void itemLore$handleClientNameUpdate(int sessionId, String rawNameMarkup);
}
