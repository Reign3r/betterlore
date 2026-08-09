package com.reign.betterlore.internal.serverapi;

import com.reign.betterlore.ModDataComponents;
import com.reign.betterlore.lore.LoreComponents;
import com.reign.betterlore.lore.LoreDocument;
import com.reign.betterlore.lore.LoreMarkupDecompiler;
import com.reign.betterlore.lore.LoreMarkupParser;
import com.reign.betterlore.lore.LoreOwnership;
import com.reign.betterlore.lore.ParseResult;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/** Compatibility facade for the earlier server-API preview implementation. */
public final class ServerApiOwnedLore {
	public static final int MAX_VISIBLE_LORE_LINES = LoreComponents.MAX_VISIBLE_LORE_LINES;

	private ServerApiOwnedLore() {
	}

	public static String snapshotMarkup(ItemStack stack) {
		return LoreMarkupDecompiler.toSafeOwnedLoreMarkup(stack);
	}

	static String snapshotMarkup(
			String rawMarkup,
			boolean hasCurrentOwnership,
			List<Component> visible
	) {
		List<Component> expectedOwned = parsedComponents(rawMarkup);
		List<Component> owned = LoreOwnership.ownedLines(
				visible,
				hasCurrentOwnership ? List.of() : expectedOwned
		);
		if (!expectedOwned.isEmpty() && owned.equals(expectedOwned)) {
			return LoreMarkupParser.toPreferredMarkup(rawMarkup);
		}
		return LoreMarkupDecompiler.toSafeComponentListMarkup(owned);
	}

	public static boolean hasCurrentOwnership(ItemStack stack) {
		return ModDataComponents.hasCurrentLoreOwnership(stack)
				|| LoreOwnership.hasInlineOwnership(visibleComponents(stack));
	}

	public static boolean equivalentToExistingLore(ItemStack stack, LoreDocument document) {
		return LoreComponents.equivalentToExistingLore(stack, document);
	}

	public static boolean canApplyTo(ItemStack stack, LoreDocument document) {
		return LoreComponents.canApplyTo(stack, document);
	}

	public static int combinedVisibleLineCount(ItemStack stack, LoreDocument document) {
		return LoreComponents.combinedVisibleLineCount(stack, document);
	}

	public static boolean normalizeOwnershipLayout(ItemStack stack) {
		return LoreComponents.normalizeOwnershipLayout(stack);
	}

	public static void applyTo(ItemStack stack, String rawMarkup, LoreDocument document) {
		LoreComponents.applyTo(stack, rawMarkup, document);
	}

	private static List<Component> visibleComponents(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines();
	}

	private static List<Component> parsedComponents(String rawMarkup) {
		if (rawMarkup == null || rawMarkup.isEmpty()) {
			return List.of();
		}
		ParseResult parsed = LoreMarkupParser.parse(rawMarkup);
		return parsed.isSuccess() ? LoreComponents.toComponents(parsed.document()) : List.of();
	}
}
