package com.reign.betterlore.forge;

import com.reign.betterlore.AnvilLoreMod;
import com.reign.betterlore.net.forge.ForgeBetterLoreNetworkingPlatform;
import net.minecraftforge.fml.common.Mod;

@Mod(AnvilLoreMod.MOD_ID)
public final class BetterLoreForgeMod {
	public BetterLoreForgeMod() {
		AnvilLoreMod.init();
		ForgeBetterLoreNetworkingPlatform.registerMessages();
	}
}
