package com.reign.betterlore.internal.serverapi;

import com.reign.betterlore.api.server.AnvilEditorDraft;
import com.reign.betterlore.api.server.AnvilEditorDraftValidation;
import com.reign.betterlore.lore.LoreMarkupParser;
import com.reign.betterlore.lore.ParseResult;

/**
 * Version-selectable, reflection-safe validator used by the server API backend.
 *
 * <p>This type is public because a release adapter may relocate the API backend
 * while retaining this byte-identical helper at its canonical package. Package
 * access would then fail even though both classes originated in the same source
 * package.</p>
 */
public final class AnvilEditorDraftValidator {
	private AnvilEditorDraftValidator() {
	}

	public static AnvilEditorDraftValidation validate(AnvilEditorDraft draft) {
		if (draft == null) {
			return result(AnvilEditorDraftValidation.Status.MISSING_DRAFT, "", "", "", "");
		}
		if (!draft.hasChanges()) {
			return result(
					AnvilEditorDraftValidation.Status.NO_CHANGES,
					"",
					"",
					draft.rawNameMarkup(),
					draft.rawLoreMarkup()
			);
		}

		ParseResult name = draft.nameEdited() ? LoreMarkupParser.parseName(draft.rawNameMarkup()) : null;
		ParseResult lore = draft.loreEdited() ? LoreMarkupParser.parse(draft.rawLoreMarkup()) : null;
		boolean nameValid = name == null || name.isSuccess();
		boolean loreValid = lore == null || lore.isSuccess();

		String normalizedName = name != null && name.isSuccess()
				? LoreMarkupParser.toPreferredNameMarkup(draft.rawNameMarkup())
				: draft.rawNameMarkup();
		String normalizedLore = lore != null && lore.isSuccess()
				? LoreMarkupParser.toPreferredMarkup(draft.rawLoreMarkup())
				: draft.rawLoreMarkup();
		String nameError = nameValid ? "" : name.errorMessage();
		String loreError = loreValid ? "" : lore.errorMessage();

		AnvilEditorDraftValidation.Status status;
		if (nameValid && loreValid) {
			status = AnvilEditorDraftValidation.Status.VALID;
		} else if (!nameValid && !loreValid) {
			status = AnvilEditorDraftValidation.Status.INVALID_NAME_AND_LORE;
		} else if (!nameValid) {
			status = AnvilEditorDraftValidation.Status.INVALID_NAME;
		} else {
			status = AnvilEditorDraftValidation.Status.INVALID_LORE;
		}
		return result(status, nameError, loreError, normalizedName, normalizedLore);
	}

	private static AnvilEditorDraftValidation result(
			AnvilEditorDraftValidation.Status status,
			String nameError,
			String loreError,
			String normalizedName,
			String normalizedLore
	) {
		return new AnvilEditorDraftValidation(
				status,
				nameError,
				loreError,
				normalizedName,
				normalizedLore
		);
	}
}
