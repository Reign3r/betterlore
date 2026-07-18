package com.reign.betterlore.access;

import java.util.List;

public interface AnvilLoreScreenBridge {
	void betterLore$acceptServerLoreState(int containerId, int sessionId, String rawLoreMarkup, String rawNameMarkup, int loreEditLevelCost);

	void betterLore$tickClientState();

	void betterLore$renderLateOverlay(Object graphicsObject, int mouseX, int mouseY);

	List<RecipeViewerArea> betterLore$getRecipeViewerExclusionAreas();
}
