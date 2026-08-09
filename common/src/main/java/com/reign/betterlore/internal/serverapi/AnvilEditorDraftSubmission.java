package com.reign.betterlore.internal.serverapi;

import com.reign.betterlore.api.server.AnvilEditorDraft;
import com.reign.betterlore.api.server.AnvilEditorDraftResult;
import com.reign.betterlore.api.server.AnvilEditorDraftValidation;

/** Pure status router between validated API input and one authoritative menu transition. */
public final class AnvilEditorDraftSubmission {
	private AnvilEditorDraftSubmission() {
	}

	public static AnvilEditorDraftResult route(
			AnvilEditorDraft draft,
			AnvilEditorDraftValidation validation,
			boolean activeAnvil,
			int activeContainerId,
			int activeSessionId,
			boolean inputPresent,
			DraftApplier applier
	) {
		if (!validation.valid()) {
			AnvilEditorDraftResult.Status status = validation.status()
					== AnvilEditorDraftValidation.Status.NO_CHANGES
					? AnvilEditorDraftResult.Status.NO_CHANGES
					: AnvilEditorDraftResult.Status.INVALID_DRAFT;
			return result(status, validation.message());
		}
		if (!activeAnvil) {
			return result(AnvilEditorDraftResult.Status.NO_ACTIVE_ANVIL,
					"No Better Lore anvil menu is active.");
		}
		if (activeContainerId != draft.containerId()) {
			return result(AnvilEditorDraftResult.Status.WRONG_CONTAINER,
					"The active anvil container changed.");
		}
		if (activeSessionId != draft.sessionId()) {
			return result(AnvilEditorDraftResult.Status.STALE_SESSION,
					"The anvil editing session changed.");
		}
		if (!inputPresent) {
			return result(AnvilEditorDraftResult.Status.EMPTY_INPUT,
					"The anvil input slot is empty.");
		}
		if (applier == null || !applier.apply(
				draft.sessionId(),
				draft.nameEdited(),
				validation.normalizedNameMarkup(),
				draft.loreEdited(),
				validation.normalizedLoreMarkup()
		)) {
			return result(AnvilEditorDraftResult.Status.STALE_SESSION,
					"The anvil editing state changed before the draft could be applied.");
		}
		return result(AnvilEditorDraftResult.Status.APPLIED, "");
	}

	private static AnvilEditorDraftResult result(AnvilEditorDraftResult.Status status, String message) {
		return new AnvilEditorDraftResult(status, message);
	}

	@FunctionalInterface
	public interface DraftApplier {
		boolean apply(
				int sessionId,
				boolean nameEdited,
				String rawNameMarkup,
				boolean loreEdited,
				String rawLoreMarkup
		);
	}
}
