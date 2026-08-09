package com.reign.betterlore.api.server;

import java.util.Objects;

/**
 * Pure validation result for an {@link AnvilEditorDraft}.
 *
 * <p>Name and lore errors are kept separately so callers receive the exact
 * parser feedback for both fields when both are invalid. Successful fields
 * also expose the canonical markup that submission will apply.</p>
 */
public record AnvilEditorDraftValidation(
		Status status,
		String nameErrorMessage,
		String loreErrorMessage,
		String normalizedNameMarkup,
		String normalizedLoreMarkup
) {
	public AnvilEditorDraftValidation {
		status = Objects.requireNonNull(status, "status");
		nameErrorMessage = nameErrorMessage == null ? "" : nameErrorMessage;
		loreErrorMessage = loreErrorMessage == null ? "" : loreErrorMessage;
		normalizedNameMarkup = normalizedNameMarkup == null ? "" : normalizedNameMarkup;
		normalizedLoreMarkup = normalizedLoreMarkup == null ? "" : normalizedLoreMarkup;
	}

	public boolean valid() {
		return status == Status.VALID;
	}

	/** Returns a concise non-null message suitable for a server-side editor. */
	public String message() {
		return switch (status) {
			case VALID -> "";
			case MISSING_DRAFT -> "Draft is required.";
			case NO_CHANGES -> "Draft does not contain any changes.";
			case INVALID_NAME -> nameErrorMessage;
			case INVALID_LORE -> loreErrorMessage;
			case INVALID_NAME_AND_LORE -> "Name: " + nameErrorMessage + " Lore: " + loreErrorMessage;
		};
	}

	public enum Status {
		VALID,
		MISSING_DRAFT,
		NO_CHANGES,
		INVALID_NAME,
		INVALID_LORE,
		INVALID_NAME_AND_LORE
	}
}
