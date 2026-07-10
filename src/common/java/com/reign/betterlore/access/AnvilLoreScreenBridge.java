package com.reign.betterlore.access;

import java.util.List;

public interface AnvilLoreScreenBridge {
	void betterLore$acceptServerLoreState(int containerId, int sessionId, String rawLoreMarkup, String rawNameMarkup);

	List<RecipeViewerArea> betterLore$getRecipeViewerExclusionAreas();
}
