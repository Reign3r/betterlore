package com.reign.betterlore.api.server;

/** Immutable snapshot of the item currently occupying an active anvil. */
public record AnvilEditorSession(
		int containerId,
		int sessionId,
		String rawNameMarkup,
		String rawLoreMarkup,
		int loreEditLevelCost
) {
	public AnvilEditorSession {
		rawNameMarkup = rawNameMarkup == null ? "" : rawNameMarkup;
		rawLoreMarkup = rawLoreMarkup == null ? "" : rawLoreMarkup;
	}
}
