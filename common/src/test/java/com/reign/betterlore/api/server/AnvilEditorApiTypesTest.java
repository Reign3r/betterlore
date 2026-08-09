package com.reign.betterlore.api.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnvilEditorApiTypesTest {
	@Test
	void serverApiV1RemainsThePublishedContract() {
		assertEquals(1, BetterLoreServerApi.API_VERSION);
	}

	@Test
	void draftNormalizesNullableStringsAndTracksIntent() {
		var draft = new AnvilEditorDraft(7, 3, true, null, false, null);

		assertEquals("", draft.rawNameMarkup());
		assertEquals("", draft.rawLoreMarkup());
		assertTrue(draft.hasChanges());
	}

	@Test
	void sessionNormalizesNullableStrings() {
		var session = new AnvilEditorSession(7, 3, null, null, 1);

		assertEquals("", session.rawNameMarkup());
		assertEquals("", session.rawLoreMarkup());
	}

	@Test
	void publicResultsNormalizeMessagesAndExposeSuccess() {
		var result = new AnvilEditorDraftResult(AnvilEditorDraftResult.Status.APPLIED, null);
		var validation = new AnvilEditorDraftValidation(
				AnvilEditorDraftValidation.Status.VALID,
				null,
				null,
				null,
				null
		);

		assertTrue(result.applied());
		assertEquals("", result.message());
		assertTrue(validation.valid());
		assertEquals("", validation.nameErrorMessage());
		assertEquals("", validation.loreErrorMessage());
		assertEquals("", validation.normalizedNameMarkup());
		assertEquals("", validation.normalizedLoreMarkup());
		assertEquals("", validation.message());
		assertFalse(new AnvilEditorDraft(0, 0, false, "", false, "").hasChanges());
	}
}
