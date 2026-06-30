package com.reign.itemlore.compat.jei;

import com.reign.itemlore.AnvilLoreMod;
import com.reign.itemlore.access.AnvilLoreScreenBridge;
import com.reign.itemlore.access.RecipeViewerArea;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;

import java.util.List;

@JeiPlugin
public final class ItemLoreJeiPlugin implements IModPlugin {
	private static final Identifier UID = Identifier.fromNamespaceAndPath(AnvilLoreMod.MOD_ID, "jei");

	@Override
	public Identifier getPluginUid() {
		return UID;
	}

	@Override
	public void registerGuiHandlers(IGuiHandlerRegistration registration) {
		registration.addGuiContainerHandler(AnvilScreen.class, new IGuiContainerHandler<AnvilScreen>() {
			@Override
			public List<Rect2i> getGuiExtraAreas(AnvilScreen screen) {
				if (!(screen instanceof AnvilLoreScreenBridge bridge)) {
					return List.of();
				}

				return bridge.itemLore$getRecipeViewerExclusionAreas().stream()
						.map(ItemLoreJeiPlugin::toRect2i)
						.toList();
			}
		});
	}

	private static Rect2i toRect2i(RecipeViewerArea area) {
		return new Rect2i(area.x(), area.y(), area.width(), area.height());
	}
}
