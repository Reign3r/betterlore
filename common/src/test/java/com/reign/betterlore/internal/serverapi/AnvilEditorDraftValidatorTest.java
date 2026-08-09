package com.reign.betterlore.internal.serverapi;

import com.reign.betterlore.api.server.AnvilEditorDraft;
import com.reign.betterlore.api.server.AnvilEditorDraftValidation;
import com.reign.betterlore.lore.LoreMarkupParser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnvilEditorDraftValidatorTest {
	@Test
	void validatorRemainsPubliclyInvocableAcrossRelocatedPackageBoundaries() throws Exception {
		Class<?> validatorClass = Class.forName(
				"com.reign.betterlore.internal.serverapi.AnvilEditorDraftValidator"
		);
		var validate = validatorClass.getMethod("validate", AnvilEditorDraft.class);
		var draft = new AnvilEditorDraft(2, 4, false, "", true, "Lore");

		assertTrue(Modifier.isPublic(validatorClass.getModifiers()));
		assertTrue(Modifier.isPublic(validate.getModifiers()));
		assertEquals(
				AnvilEditorDraftValidation.Status.VALID,
				((AnvilEditorDraftValidation) validate.invoke(null, draft)).status()
		);
	}

	@Test
	void missingAndNoChangeDraftsAreRejectedPurely() {
		assertEquals(
				AnvilEditorDraftValidation.Status.MISSING_DRAFT,
				AnvilEditorDraftValidator.validate(null).status()
		);
		assertEquals(
				AnvilEditorDraftValidation.Status.NO_CHANGES,
				AnvilEditorDraftValidator.validate(
						new AnvilEditorDraft(2, 4, false, "unused", false, "unused")
				).status()
		);
	}

	@Test
	void validatesBothEditedFieldsBeforeReportingFailure() {
		String invalidName = "Bad \u00a7cName";
		String invalidLore = "a".repeat(LoreMarkupParser.MAX_VISIBLE_CODEPOINTS + 1);
		var validation = AnvilEditorDraftValidator.validate(
				new AnvilEditorDraft(2, 4, true, invalidName, true, invalidLore)
		);

		assertEquals(AnvilEditorDraftValidation.Status.INVALID_NAME_AND_LORE, validation.status());
		assertEquals(LoreMarkupParser.parseName(invalidName).errorMessage(), validation.nameErrorMessage());
		assertEquals(LoreMarkupParser.parse(invalidLore).errorMessage(), validation.loreErrorMessage());
		assertFalse(validation.valid());
		assertTrue(validation.message().contains(validation.nameErrorMessage()));
		assertTrue(validation.message().contains(validation.loreErrorMessage()));
	}

	@Test
	void canonicalizesEverySuccessfulEditedField() {
		String rawName = "<c #FF6600>Name</c>";
		String rawLore = "<c #00FF00>Lore</c>";
		var validation = AnvilEditorDraftValidator.validate(
				new AnvilEditorDraft(2, 4, true, rawName, true, rawLore)
		);

		assertEquals(AnvilEditorDraftValidation.Status.VALID, validation.status());
		assertEquals(LoreMarkupParser.toPreferredNameMarkup(rawName), validation.normalizedNameMarkup());
		assertEquals(LoreMarkupParser.toPreferredMarkup(rawLore), validation.normalizedLoreMarkup());
	}

	@Test
	void ignoresContentOfFieldsThatWereNotEdited() {
		String invalidName = "First\nSecond";
		var validation = AnvilEditorDraftValidator.validate(
				new AnvilEditorDraft(2, 4, false, invalidName, true, "Valid lore")
		);

		assertEquals(AnvilEditorDraftValidation.Status.VALID, validation.status());
		assertEquals(invalidName, validation.normalizedNameMarkup());
	}
}
