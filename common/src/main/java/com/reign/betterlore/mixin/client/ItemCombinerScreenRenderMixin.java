package com.reign.betterlore.mixin.client;

import com.reign.betterlore.access.AnvilLoreScreenBridge;
//? if <26.1 {
import net.minecraft.client.gui.GuiGraphics;
//? }
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stable late-render hook used by component-era screens.
 *
 * <p>Before 26.1, {@code AnvilScreen} inherits {@code render} from
 * {@code ItemCombinerScreen}. Injecting into the superclass avoids a missing
 * target on versions where the concrete anvil screen does not override it.</p>
 */
@Mixin(ItemCombinerScreen.class)
public abstract class ItemCombinerScreenRenderMixin {
	//? if <26.1 {
	@Inject(method = "render", at = @At("TAIL"))
	private void betterLore$renderAnvilOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if ((Object) this instanceof AnvilLoreScreenBridge bridge) {
			bridge.betterLore$renderLateOverlay(graphics, mouseX, mouseY);
		}
	}
	//? }
}
