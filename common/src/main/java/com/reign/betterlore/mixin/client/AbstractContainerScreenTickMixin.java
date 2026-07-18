package com.reign.betterlore.mixin.client;

import com.reign.betterlore.access.AnvilLoreScreenBridge;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stable client tick hook for the anvil editor.
 *
 * <p>Several component-era Minecraft versions inherit {@code containerTick}
 * instead of overriding it in {@code AnvilScreen}; targeting the common
 * container-screen base prevents a required mixin injection from disappearing
 * on those versions.</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenTickMixin {
	@Inject(method = "containerTick", at = @At("TAIL"))
	private void betterLore$tickAnvilEditor(CallbackInfo ci) {
		if ((Object) this instanceof AnvilLoreScreenBridge bridge) {
			bridge.betterLore$tickClientState();
		}
	}
}
