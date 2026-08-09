package com.reign.betterlore.internal.serverapi;

import com.reign.betterlore.lore.LoreOwnership;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Compatibility facade for the earlier server-API preview implementation.
 *
 * <p>Ownership is now a core Better Lore rule shared by native and server-API
 * edits; keeping this facade avoids two marker formats or partition engines.</p>
 */
@Deprecated(forRemoval = false)
public final class ServerApiLoreOwnership {
	private ServerApiLoreOwnership() {
	}

	public static List<Component> compose(
			List<Component> visible,
			List<Component> legacyOwned,
			List<Component> replacementOwned
	) {
		return LoreOwnership.compose(visible, legacyOwned, replacementOwned);
	}

	public static List<Component> ownedLines(List<Component> visible, List<Component> legacyOwned) {
		return LoreOwnership.ownedLines(visible, legacyOwned);
	}

	public static List<Component> foreignLines(List<Component> visible, List<Component> legacyOwned) {
		return LoreOwnership.foreignLines(visible, legacyOwned);
	}

	public static boolean hasInlineOwnership(List<Component> visible) {
		return LoreOwnership.hasInlineOwnership(visible);
	}

	public static boolean isOwnedLine(Component line) {
		return LoreOwnership.isOwnedLine(line);
	}

	public static boolean isSeparator(Component line) {
		return LoreOwnership.isSeparator(line);
	}
}
