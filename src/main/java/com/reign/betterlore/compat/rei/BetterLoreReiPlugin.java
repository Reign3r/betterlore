package com.reign.betterlore.compat.rei;

import com.reign.betterlore.access.AnvilLoreScreenBridge;
import com.reign.betterlore.access.RecipeViewerArea;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;

import java.util.List;

public final class BetterLoreReiPlugin implements REIClientPlugin {
	@Override
	public void registerExclusionZones(ExclusionZones zones) {
		zones.register(AnvilScreen.class, screen -> {
			if (!(screen instanceof AnvilLoreScreenBridge bridge)) {
				return List.of();
			}

			return bridge.betterLore$getRecipeViewerExclusionAreas().stream()
					.map(BetterLoreReiPlugin::toRectangle)
					.toList();
		});
	}

	private static Rectangle toRectangle(RecipeViewerArea area) {
		return new Rectangle(area.x(), area.y(), area.width(), area.height());
	}
}
