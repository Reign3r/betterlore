package com.reign.betterlore.client;

/** Keeps the wide help tooltip on-screen without displacing short button hints. */
public final class TooltipPositioning {
	private static final int HELP_TOOLTIP_WIDTH = 340;

	private TooltipPositioning() {
	}

	public static int anchorX(int mouseX, int screenWidth, boolean helpTooltip) {
		return helpTooltip
				? Math.min(mouseX, Math.max(0, screenWidth - HELP_TOOLTIP_WIDTH))
				: mouseX;
	}

	/**
	 * Uses the center of a short button as the cursor anchor consumed by
	 * Minecraft's default tooltip positioner. The positioner adds/subtracts its
	 * own 12-pixel cursor margin, so anchoring to the button center keeps the
	 * tooltip close to either side of an 18-pixel button without overlapping it.
	 */
	public static int buttonAnchorX(int buttonX, int buttonWidth) {
		return buttonX + buttonWidth / 2;
	}
}
