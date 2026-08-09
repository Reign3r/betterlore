package com.reign.betterlore.api.server;

/**
 * A complete Better Lore edit submitted for one active anvil session.
 *
 * <p>The edit flags distinguish an intentionally cleared field from a field
 * the caller did not edit. Strings are always normalized to non-null values.</p>
 */
public record AnvilEditorDraft(
		int containerId,
		int sessionId,
		boolean nameEdited,
		String rawNameMarkup,
		boolean loreEdited,
		String rawLoreMarkup
) {
	public AnvilEditorDraft {
		rawNameMarkup = rawNameMarkup == null ? "" : rawNameMarkup;
		rawLoreMarkup = rawLoreMarkup == null ? "" : rawLoreMarkup;
	}

	public boolean hasChanges() {
		return nameEdited || loreEdited;
	}
}
