package com.reign.betterlore.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Gives the compatibility mixin plugin a transformation point for JEI's
 * Minecraft 1.21.11 resource-identifier return-type rename.
 */
@Pseudo
@Mixin(targets = "com.reign.betterlore.compat.jei.BetterLoreJeiPlugin", remap = false)
public abstract class BetterLoreJeiPluginCompatibilityMixin {
}
