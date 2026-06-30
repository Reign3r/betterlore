package com.reign.itemlore.access;

import java.util.List;

public interface AnvilLoreScreenBridge {
	void itemLore$acceptServerLoreState(int containerId, int sessionId, String rawLoreMarkup, String rawNameMarkup);

	List<RecipeViewerArea> itemLore$getRecipeViewerExclusionAreas();
}
