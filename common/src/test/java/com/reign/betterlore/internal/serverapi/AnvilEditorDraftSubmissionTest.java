package com.reign.betterlore.internal.serverapi;

import com.reign.betterlore.access.AnvilLoreMenuBridge;
import com.reign.betterlore.api.server.AnvilEditorDraft;
import com.reign.betterlore.api.server.AnvilEditorDraftResult;
import com.reign.betterlore.api.server.AnvilEditorDraftValidation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnvilEditorDraftSubmissionTest {
	@Test
	void routesValidationAndMenuStateFailuresWithoutMutatingTheBridge() {
		var bridge = new RecordingBridge();
		var draft = new AnvilEditorDraft(7, 3, true, "Name", true, "Lore");
		var valid = AnvilEditorDraftValidator.validate(draft);

		assertStatus(
				AnvilEditorDraftResult.Status.NO_CHANGES,
				route(
						new AnvilEditorDraft(7, 3, false, "", false, ""),
						false,
						7,
						3,
						true,
						bridge
				)
		);
		assertStatus(
				AnvilEditorDraftResult.Status.INVALID_DRAFT,
				route(
						new AnvilEditorDraft(7, 3, true, "Bad \u00a7cName", false, ""),
						false,
						7,
						3,
						true,
						bridge
				)
		);
		assertStatus(
				AnvilEditorDraftResult.Status.NO_ACTIVE_ANVIL,
				AnvilEditorDraftSubmission.route(draft, valid, false, 0, 0, false, null)
		);
		assertStatus(
				AnvilEditorDraftResult.Status.WRONG_CONTAINER,
				AnvilEditorDraftSubmission.route(
						draft, valid, true, 8, 3, true, bridge::betterLore$handleServerDraft
				)
		);
		assertStatus(
				AnvilEditorDraftResult.Status.STALE_SESSION,
				AnvilEditorDraftSubmission.route(
						draft, valid, true, 7, 4, true, bridge::betterLore$handleServerDraft
				)
		);
		assertStatus(
				AnvilEditorDraftResult.Status.EMPTY_INPUT,
				AnvilEditorDraftSubmission.route(
						draft, valid, true, 7, 3, false, bridge::betterLore$handleServerDraft
				)
		);

		assertEquals(0, bridge.serverDraftCalls);
	}

	@Test
	void submitsBothEditedFieldsThroughOneCombinedBridgeInvocation() {
		var bridge = new RecordingBridge();
		String rawName = "<c #FF6600>Name</c>";
		String rawLore = "<c #00FF00>Lore</c>";
		var draft = new AnvilEditorDraft(7, 3, true, rawName, true, rawLore);
		var validation = AnvilEditorDraftValidator.validate(draft);

		var result = AnvilEditorDraftSubmission.route(
				draft,
				validation,
				true,
				7,
				3,
				true,
				bridge::betterLore$handleServerDraft
		);

		assertTrue(result.applied());
		assertEquals(1, bridge.serverDraftCalls);
		assertEquals(0, bridge.clientNameCalls);
		assertEquals(0, bridge.clientLoreCalls);
		assertEquals(3, bridge.sessionId);
		assertTrue(bridge.nameEdited);
		assertEquals(validation.normalizedNameMarkup(), bridge.rawNameMarkup);
		assertTrue(bridge.loreEdited);
		assertEquals(validation.normalizedLoreMarkup(), bridge.rawLoreMarkup);
	}

	@Test
	void reportsAStateChangeWhenTheCombinedBridgeTransitionRejectsTheDraft() {
		var bridge = new RecordingBridge();
		bridge.acceptServerDraft = false;
		var draft = new AnvilEditorDraft(7, 3, false, "", true, "Lore");

		var result = route(draft, true, 7, 3, true, bridge);

		assertStatus(AnvilEditorDraftResult.Status.STALE_SESSION, result);
		assertEquals(1, bridge.serverDraftCalls);
		assertFalse(result.message().isEmpty());
	}

	private static AnvilEditorDraftResult route(
			AnvilEditorDraft draft,
			boolean activeAnvil,
			int activeContainerId,
			int activeSessionId,
			boolean inputPresent,
			RecordingBridge bridge
	) {
		AnvilEditorDraftValidation validation = AnvilEditorDraftValidator.validate(draft);
		return AnvilEditorDraftSubmission.route(
				draft,
				validation,
				activeAnvil,
				activeContainerId,
				activeSessionId,
				inputPresent,
				bridge::betterLore$handleServerDraft
		);
	}

	private static void assertStatus(
			AnvilEditorDraftResult.Status expected,
			AnvilEditorDraftResult actual
	) {
		assertEquals(expected, actual.status());
	}

	private static final class RecordingBridge implements AnvilLoreMenuBridge {
		private boolean acceptServerDraft = true;
		private int clientLoreCalls;
		private int clientNameCalls;
		private int serverDraftCalls;
		private int sessionId;
		private boolean nameEdited;
		private String rawNameMarkup;
		private boolean loreEdited;
		private String rawLoreMarkup;

		@Override
		public int betterLore$getSessionId() {
			return sessionId;
		}

		@Override
		public void betterLore$setLoreEditLevelCost(int loreEditLevelCost) {
		}

		@Override
		public void betterLore$handleClientLoreUpdate(int sessionId, String rawLoreMarkup) {
			clientLoreCalls++;
		}

		@Override
		public void betterLore$handleClientNameUpdate(int sessionId, String rawNameMarkup) {
			clientNameCalls++;
		}

		@Override
		public boolean betterLore$handleServerDraft(
				int sessionId,
				boolean nameEdited,
				String rawNameMarkup,
				boolean loreEdited,
				String rawLoreMarkup
		) {
			serverDraftCalls++;
			this.sessionId = sessionId;
			this.nameEdited = nameEdited;
			this.rawNameMarkup = rawNameMarkup;
			this.loreEdited = loreEdited;
			this.rawLoreMarkup = rawLoreMarkup;
			return acceptServerDraft;
		}
	}
}
