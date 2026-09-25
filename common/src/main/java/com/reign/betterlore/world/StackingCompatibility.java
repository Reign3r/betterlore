package com.reign.betterlore.world;

import com.reign.betterlore.AnvilLoreMod;
import com.reign.betterlore.ModDataComponents;
import com.reign.betterlore.lore.LoreOwnership;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/**
 * Keeps Better Lore's editable source data from changing vanilla stack identity.
 *
 * <p>The source markup is deliberately retained in {@code minecraft:custom_data}
 * so editors and migration can recover the exact formatting. That data is
 * private implementation state, however, and must not prevent two stacks with
 * the same visible item components from merging.</p>
 */
public final class StackingCompatibility {
	private StackingCompatibility() {
	}

	/**
	 * Returns whether two stacks are equal after removing only Better Lore's
	 * private custom-data root. All visible and unrelated item components remain
	 * part of the comparison.
	 */
	public static boolean matchesIgnoringBetterLore(ItemStack left, ItemStack right) {
		if (!hasBetterLoreData(left) && !hasBetterLoreData(right)) {
			return false;
		}
		if (!ItemStack.isSameItem(left, right)) {
			return false;
		}
		if (left.isEmpty() || right.isEmpty()) {
			return left.isEmpty() && right.isEmpty();
		}

		if (!sanitizedComponents(left).equals(sanitizedComponents(right))) {
			return false;
		}

		// Normalize the older representation before vanilla performs the merge.
		// This keeps the current source and ownership markers on whichever stack
		// Minecraft chooses as the merge destination.
		preferCurrentRepresentation(left, right);
		return true;
	}

	/** Returns whether the full component maps differ only in Better Lore data. */
	public static boolean differsOnlyByBetterLore(ItemStack left, ItemStack right) {
		return matchesIgnoringBetterLore(left, right)
				&& !left.getComponents().equals(right.getComponents());
	}

	private static boolean hasBetterLoreData(ItemStack stack) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		return customData != null
				&& !customData.isEmpty()
				&& customData.copyTag().get(AnvilLoreMod.MOD_ID) != null;
	}

	private static DataComponentMap sanitizedComponents(ItemStack stack) {
		ItemStack sanitized = stack.copy();
		CustomData customData = sanitized.get(DataComponents.CUSTOM_DATA);
		if (customData != null && !customData.isEmpty()) {
			CompoundTag root = customData.copyTag();
			if (root.get(AnvilLoreMod.MOD_ID) != null) {
				root.remove(AnvilLoreMod.MOD_ID);
				if (root.isEmpty()) {
					sanitized.remove(DataComponents.CUSTOM_DATA);
				} else {
					sanitized.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
				}
			}
		}
		removeOwnershipMarkers(sanitized);
		return sanitized.getComponents();
	}

	private static void removeOwnershipMarkers(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null || lore.lines().isEmpty()) {
			return;
		}

		List<net.minecraft.network.chat.Component> lines = lore.lines();
		List<net.minecraft.network.chat.Component> sanitized = lines.stream()
				.map(line -> LoreOwnership.isOwnedLine(line) || LoreOwnership.isSeparator(line)
						? line.copy().withStyle(style -> style.withInsertion(null))
						: line)
				.toList();
		if (!lines.equals(sanitized)) {
			stack.set(DataComponents.LORE, new ItemLore(sanitized));
		}
	}

	private static void preferCurrentRepresentation(ItemStack left, ItemStack right) {
		boolean leftCurrent = isCurrentRepresentation(left);
		boolean rightCurrent = isCurrentRepresentation(right);
		if (leftCurrent == rightCurrent) {
			return;
		}

		ItemStack current = leftCurrent ? left : right;
		ItemStack legacy = leftCurrent ? right : left;
		copyBetterLoreData(current, legacy);
		copyLoreOwnership(current, legacy);
	}

	private static boolean isCurrentRepresentation(ItemStack stack) {
		if (ModDataComponents.hasCurrentLoreOwnership(stack)
				|| ModDataComponents.hasCurrentNameOwnership(stack)) {
			return true;
		}

		ItemLore lore = stack.get(DataComponents.LORE);
		return lore != null && lore.lines().stream()
				.anyMatch(line -> LoreOwnership.isOwnedLine(line) || LoreOwnership.isSeparator(line));
	}

	private static void copyBetterLoreData(ItemStack source, ItemStack target) {
		CustomData sourceData = source.get(DataComponents.CUSTOM_DATA);
		if (sourceData == null || sourceData.isEmpty()) {
			return;
		}
		CompoundTag sourceRoot = sourceData.copyTag();
		if (!(sourceRoot.get(AnvilLoreMod.MOD_ID) instanceof CompoundTag betterLore)) {
			return;
		}

		CompoundTag targetRoot = target.get(DataComponents.CUSTOM_DATA) == null
				? new CompoundTag()
				: target.get(DataComponents.CUSTOM_DATA).copyTag();
		targetRoot.remove(AnvilLoreMod.MOD_ID);
		targetRoot.put(AnvilLoreMod.MOD_ID, betterLore.copy());

		if (targetRoot.isEmpty()) {
			target.remove(DataComponents.CUSTOM_DATA);
		} else {
			target.set(DataComponents.CUSTOM_DATA, CustomData.of(targetRoot));
		}
	}

	private static void copyLoreOwnership(ItemStack source, ItemStack target) {
		ItemLore sourceLore = source.get(DataComponents.LORE);
		if (sourceLore == null) {
			target.remove(DataComponents.LORE);
		} else {
			target.set(DataComponents.LORE, new ItemLore(sourceLore.lines()));
		}
	}
}
