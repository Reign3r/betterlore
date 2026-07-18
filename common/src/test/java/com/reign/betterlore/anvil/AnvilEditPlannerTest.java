package com.reign.betterlore.anvil;

import org.junit.jupiter.api.Test;

import static com.reign.betterlore.anvil.AnvilEditPlanner.Outcome.APPLY;
import static com.reign.betterlore.anvil.AnvilEditPlanner.Outcome.CLEAR_OUTPUT;
import static com.reign.betterlore.anvil.AnvilEditPlanner.Outcome.INVALID;
import static com.reign.betterlore.anvil.AnvilEditPlanner.Outcome.PASS_THROUGH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnvilEditPlannerTest {
	@Test
	void rejectsInvalidMarkup() {
		var plan = plan(false, false, false, true, false, false, 1);
		assertEquals(INVALID, plan.outcome());
		assertEquals(0, plan.finalCost());
	}

	@Test
	void preservesInvalidVanillaRightInputCombination() {
		var plan = plan(true, true, false, true, false, false, 0);
		assertEquals(PASS_THROUGH, plan.outcome());
	}

	@Test
	void unchangedTextDoesNotCreateAnOutput() {
		var plan = plan(true, false, false, false, false, false, 0);
		assertEquals(PASS_THROUGH, plan.outcome());
	}

	@Test
	void loreOnlyCostsOneLevelAndCopiesLeftItem() {
		var plan = plan(true, false, false, true, false, false, 0);
		assertEquals(APPLY, plan.outcome());
		assertEquals(1, plan.finalCost());
		assertTrue(plan.applyLore());
		assertFalse(plan.applyName());
		assertTrue(plan.copyLeftAsOutput());
	}

	@Test
	void formattedRenameUsesVanillaRenameLevel() {
		var plan = plan(true, false, true, false, true, true, 1);
		assertEquals(APPLY, plan.outcome());
		assertEquals(1, plan.finalCost());
		assertTrue(plan.applyName());
	}

	@Test
	void nameAndLoreCostsVanillaRenamePlusOne() {
		var plan = plan(true, false, true, true, true, true, 1);
		assertEquals(APPLY, plan.outcome());
		assertEquals(2, plan.finalCost());
		assertTrue(plan.applyName());
		assertTrue(plan.applyLore());
	}

	@Test
	void customNameCostsOneWhenVanillaDidNotApplyIt() {
		var plan = plan(true, false, false, false, true, false, 0);
		assertEquals(APPLY, plan.outcome());
		assertEquals(1, plan.finalCost());
	}

	@Test
	void loreAddsOneToRepairOrCombinationCost() {
		var plan = plan(true, true, true, true, false, false, 7);
		assertEquals(APPLY, plan.outcome());
		assertEquals(8, plan.finalCost());
	}

	@Test
	void loreCanBeConfiguredFree() {
		var plan = plan(true, false, false, true, false, false, 0, 0);
		assertEquals(APPLY, plan.outcome());
		assertEquals(0, plan.finalCost());
		assertTrue(plan.applyLore());
	}

	@Test
	void loreCanCostUpTo255Levels() {
		var plan = plan(true, false, false, true, false, false, 0, 255);
		assertEquals(APPLY, plan.outcome());
		assertEquals(255, plan.finalCost());
	}

	@Test
	void plannerClampsUntrustedLoreCost() {
		assertEquals(0, plan(true, false, false, true, false, false, 0, -1).finalCost());
		assertEquals(255, plan(true, false, false, true, false, false, 0, 256).finalCost());
	}

	@Test
	void rawVanillaRenameEquivalentToExistingNameIsCleared() {
		var plan = plan(true, false, true, false, false, true, 1);
		assertEquals(CLEAR_OUTPUT, plan.outcome());
		assertEquals(0, plan.finalCost());
	}

	@Test
	void rawNameCorrectionInCombinationRemovesSpuriousRenameLevel() {
		var plan = plan(true, true, true, true, false, true, 6);
		assertEquals(APPLY, plan.outcome());
		assertEquals(6, plan.finalCost());
		assertTrue(plan.applyName());
		assertTrue(plan.applyLore());
	}

	private static AnvilEditPlanner.Plan plan(
			boolean parseValid,
			boolean rightPresent,
			boolean outputPresent,
			boolean loreChanged,
			boolean nameChanged,
			boolean rawNameApplied,
			int cost
	) {
		return plan(parseValid, rightPresent, outputPresent, loreChanged, nameChanged, rawNameApplied, cost, 1);
	}

	private static AnvilEditPlanner.Plan plan(
			boolean parseValid,
			boolean rightPresent,
			boolean outputPresent,
			boolean loreChanged,
			boolean nameChanged,
			boolean rawNameApplied,
			int cost,
			int loreEditLevelCost
	) {
		return AnvilEditPlanner.plan(new AnvilEditPlanner.Input(
				parseValid,
				rightPresent,
				outputPresent,
				loreChanged,
				nameChanged,
				rawNameApplied,
				cost,
				loreEditLevelCost
		));
	}
}
