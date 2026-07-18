package com.reign.betterlore.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BetterLoreConfigTest {
	@Test
	void acceptsFullConfiguredRange() {
		assertEquals(0, BetterLoreConfig.parseLevelCost("0"));
		assertEquals(255, BetterLoreConfig.parseLevelCost("255"));
	}

	@Test
	void clampsOutOfRangeValues() {
		assertEquals(0, BetterLoreConfig.parseLevelCost("-12"));
		assertEquals(255, BetterLoreConfig.parseLevelCost("999"));
	}

	@Test
	void malformedValuesUseDefault() {
		assertEquals(BetterLoreConfig.DEFAULT_LORE_EDIT_LEVEL_COST, BetterLoreConfig.parseLevelCost("not-a-number"));
		assertEquals(BetterLoreConfig.DEFAULT_LORE_EDIT_LEVEL_COST, BetterLoreConfig.parseLevelCost(null));
	}
}
