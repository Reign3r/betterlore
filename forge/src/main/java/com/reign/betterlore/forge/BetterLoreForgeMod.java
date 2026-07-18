package com.reign.betterlore.forge;

import com.reign.betterlore.AnvilLoreMod;
import com.reign.betterlore.net.forge.ForgeBetterLoreNetworkingPlatform;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(AnvilLoreMod.MOD_ID)
public final class BetterLoreForgeMod {
	public BetterLoreForgeMod() {
		AnvilLoreMod.init();
		ForgeBetterLoreNetworkingPlatform.registerMessages();
		if (FMLEnvironment.dist == Dist.CLIENT) {
			BetterLoreForgeClientMod.initialize();
		}
	}
}
