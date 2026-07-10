package com.reign.betterlore.neoforge;

import com.reign.betterlore.AnvilLoreMod;
import com.reign.betterlore.net.neoforge.NeoForgeBetterLoreNetworkingPlatform;
import com.reign.betterlore.net.neoforge.NeoForgeClientPayloadHandlers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.DistExecutor;
import net.neoforged.fml.common.Mod;

@Mod(AnvilLoreMod.MOD_ID)
public final class BetterLoreNeoForgeMod {
	public BetterLoreNeoForgeMod(IEventBus modEventBus) {
		AnvilLoreMod.init();
		modEventBus.addListener(NeoForgeBetterLoreNetworkingPlatform::registerPayloadHandlers);
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> modEventBus.addListener(NeoForgeClientPayloadHandlers::registerClientPayloadHandlers));
	}
}
