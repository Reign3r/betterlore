package com.reign.betterlore.access;

public interface AnvilLoreMenuBridge {
	int betterLore$getSessionId();

	void betterLore$setLoreEditLevelCost(int loreEditLevelCost);

	void betterLore$handleClientLoreUpdate(int sessionId, String rawLoreMarkup);

	void betterLore$handleClientNameUpdate(int sessionId, String rawNameMarkup);

	boolean betterLore$handleServerDraft(
			int sessionId,
			boolean nameEdited,
			String rawNameMarkup,
			boolean loreEdited,
			String rawLoreMarkup
	);
}
