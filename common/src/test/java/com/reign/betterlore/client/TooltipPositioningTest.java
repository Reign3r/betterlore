package com.reign.betterlore.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TooltipPositioningTest {
	@Test
	void shortButtonTooltipStaysAtCursor() {
		assertEquals(401, TooltipPositioning.anchorX(401, 512, false));
	}

	@Test
	void wideHelpTooltipKeepsItsExistingScreenClamp() {
		assertEquals(172, TooltipPositioning.anchorX(401, 512, true));
		assertEquals(100, TooltipPositioning.anchorX(100, 512, true));
		assertEquals(0, TooltipPositioning.anchorX(100, 300, true));
	}

	@Test
	void shortButtonTooltipUsesStableButtonCenterInsteadOfHoverPosition() {
		int anchor = TooltipPositioning.buttonAnchorX(400, 18);
		assertEquals(409, anchor);
		// DefaultTooltipPositioner uses a 12-GUI-pixel horizontal margin.
		assertEquals(3, 400 - (anchor - 12));
		assertEquals(3, (anchor + 12) - (400 + 18));
		assertEquals(409, TooltipPositioning.buttonAnchorX(400, 19));
	}
}
