package com.reign.betterlore.compat.jei;

import com.reign.betterlore.AnvilLoreMod;
import com.reign.betterlore.access.AnvilLoreScreenBridge;
import com.reign.betterlore.access.RecipeViewerArea;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.renderer.Rect2i;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//? } else {
import net.minecraft.resources.ResourceLocation;
//? }

import java.util.List;

@JeiPlugin
public final class BetterLoreJeiPlugin implements IModPlugin {
	//? if >=1.21.11 {
	private static final Identifier UID = Identifier.fromNamespaceAndPath(AnvilLoreMod.MOD_ID, "jei");

	@Override
	public Identifier getPluginUid() {
		return UID;
	}
	//? } else if >=1.21 {
	private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(AnvilLoreMod.MOD_ID, "jei");

	@Override
	public ResourceLocation getPluginUid() {
		return UID;
	}
	//? } else {
	private static final ResourceLocation UID = new ResourceLocation(AnvilLoreMod.MOD_ID, "jei");

	@Override
	public ResourceLocation getPluginUid() {
		return UID;
	}
	//? }

	@Override
	public void registerGuiHandlers(IGuiHandlerRegistration registration) {
		registration.addGuiContainerHandler(AnvilScreen.class, new IGuiContainerHandler<AnvilScreen>() {
			@Override
			public List<Rect2i> getGuiExtraAreas(AnvilScreen screen) {
				if (!(screen instanceof AnvilLoreScreenBridge bridge)) {
					return List.of();
				}

				return bridge.betterLore$getRecipeViewerExclusionAreas().stream()
						.map(BetterLoreJeiPlugin::toRect2i)
						.toList();
			}
		});
	}

	private static Rect2i toRect2i(RecipeViewerArea area) {
		return new Rect2i(area.x(), area.y(), area.width(), area.height());
	}
}
