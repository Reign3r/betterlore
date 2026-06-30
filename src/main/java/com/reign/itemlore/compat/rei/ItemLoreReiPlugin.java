package com.reign.itemlore.compat.rei;

import com.reign.itemlore.access.AnvilLoreScreenBridge;
import com.reign.itemlore.access.RecipeViewerArea;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;

import java.util.List;

public final class ItemLoreReiPlugin implements REIClientPlugin {
	@Override
	public void registerExclusionZones(ExclusionZones zones) {
		zones.register(AnvilScreen.class, screen -> {
			if (!(screen instanceof AnvilLoreScreenBridge bridge)) {
				return List.of();
			}

			return bridge.itemLore$getRecipeViewerExclusionAreas().stream()
					.map(ItemLoreReiPlugin::toRectangle)
					.toList();
		});
	}

	private static Rectangle toRectangle(RecipeViewerArea area) {
		return new Rectangle(area.x(), area.y(), area.width(), area.height());
	}
}
