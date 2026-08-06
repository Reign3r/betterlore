package com.reign.betterlore.forge;

import com.reign.betterlore.AnvilLoreMod;
import com.reign.betterlore.net.AnvilLoreNetworking;
import com.reign.betterlore.net.forge.ForgeBetterLoreNetworkingPlatform;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

public final class BetterLoreForgeMod {
	public BetterLoreForgeMod() {
		AnvilLoreNetworking.installPlatform(new ForgeBetterLoreNetworkingPlatform());
		AnvilLoreMod.init();
		if (FMLEnvironment.dist == Dist.CLIENT) {
			BetterLoreForgeClientMod.initialize();
		}
	}
}
