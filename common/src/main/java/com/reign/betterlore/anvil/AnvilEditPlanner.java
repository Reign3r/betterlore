package com.reign.betterlore.anvil;

import com.reign.betterlore.config.BetterLoreConfig;

/**
 * Pure decision model for Better Lore's contribution to an anvil result.
 *
 * <p>Minecraft-specific item copying and component application stay in the
 * mixin, while this class owns the cost and outcome rules so they can be tested
 * identically for every loader and Minecraft version.</p>
 */
public final class AnvilEditPlanner {
	private AnvilEditPlanner() {
	}

	public static Plan plan(Input input) {
		if (!input.parseValid()) {
			return new Plan(Outcome.INVALID, 0, false, false, false);
		}

		// A non-empty right input with no vanilla result is an invalid vanilla
		// combination. Better Lore must not turn it into a lore-only operation.
		if (!input.vanillaOutputPresent() && input.rightInputPresent()) {
			return Plan.passThrough(input.vanillaCost());
		}

		if (!input.loreChanged() && !input.nameChanged() && !input.vanillaAppliedRawName()) {
			return Plan.passThrough(input.vanillaCost());
		}

		// Vanilla can briefly create a raw-tag rename result even when the parsed
		// custom name is equivalent to the current name. Do not expose that result.
		if (!input.loreChanged()
				&& !input.nameChanged()
				&& input.vanillaAppliedRawName()
				&& !input.rightInputPresent()) {
			return new Plan(Outcome.CLEAR_OUTPUT, 0, false, false, false);
		}

		int baseCost = Math.max(0, input.vanillaCost());
		boolean applyName = input.nameChanged();
		int extraCost = 0;

		if (input.vanillaAppliedRawName() && !input.nameChanged()) {
			baseCost = Math.max(0, baseCost - 1);
			applyName = true;
		}

		if (input.nameChanged() && !input.vanillaAppliedRawName()) {
			extraCost++;
		}
		if (input.loreChanged()) {
			extraCost += BetterLoreConfig.clampLoreEditLevelCost(input.loreEditLevelCost());
		}

		return new Plan(
				Outcome.APPLY,
				baseCost + extraCost,
				applyName,
				input.loreChanged(),
				!input.vanillaOutputPresent()
		);
	}

	public enum Outcome {
		PASS_THROUGH,
		INVALID,
		CLEAR_OUTPUT,
		APPLY
	}

	public record Input(
			boolean parseValid,
			boolean rightInputPresent,
			boolean vanillaOutputPresent,
			boolean loreChanged,
			boolean nameChanged,
			boolean vanillaAppliedRawName,
			int vanillaCost,
			int loreEditLevelCost
	) {
	}

	public record Plan(
			Outcome outcome,
			int finalCost,
			boolean applyName,
			boolean applyLore,
			boolean copyLeftAsOutput
	) {
		private static Plan passThrough(int cost) {
			return new Plan(Outcome.PASS_THROUGH, Math.max(0, cost), false, false, false);
		}
	}
}
