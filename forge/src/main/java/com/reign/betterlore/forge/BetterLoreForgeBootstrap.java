package com.reign.betterlore.forge;

import com.reign.betterlore.AnvilLoreMod;
import com.reign.betterlore.compat.CompatibilityRuntime;
import net.minecraftforge.fml.common.Mod;

/** Stable Forge mod container shared by exact builds and release adapters. */
@Mod(AnvilLoreMod.MOD_ID)
public final class BetterLoreForgeBootstrap {
	private static final String IMPLEMENTATION = "com.reign.betterlore.forge.BetterLoreForgeMod";

	public BetterLoreForgeBootstrap() {
		CompatibilityRuntime.initialize("forge");
		CompatibilityRuntime.instantiate(IMPLEMENTATION);
	}
}
