package com.reign.betterlore.api.server;

import com.reign.betterlore.compat.CompatibilityRuntime;

/** Entry point for the stable Better Lore server API. */
public final class BetterLoreServerApis {
	private static final String IMPLEMENTATION_CLASS =
			"com.reign.betterlore.internal.serverapi.BetterLoreServerApiImpl";

	private BetterLoreServerApis() {
	}

	public static BetterLoreServerApi get() {
		return Holder.INSTANCE;
	}

	private static final class Holder {
		private static final BetterLoreServerApi INSTANCE = CompatibilityRuntime.instantiate(
				IMPLEMENTATION_CLASS,
				BetterLoreServerApi.class
		);
	}
}
