package com.reign.betterlore.world;

import com.reign.betterlore.AnvilLoreMod;
import com.reign.betterlore.ModDataComponents;
import com.reign.betterlore.access.EntityItemTextCarrier;
import com.reign.betterlore.lore.LoreComponents;
import com.reign.betterlore.lore.LoreMarkupParser;
import com.reign.betterlore.lore.ParseResult;
import com.reign.betterlore.world.compat.ComponentListNbtCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/** Restores Better Lore text after an item has made an item/entity/item round trip. */
public final class EntityItemText {
	private static final String ENTITY_SNAPSHOT_KEY = "better_lore_item_text";
	private static final String FOREIGN_LORE_COMPONENTS_KEY = "foreign_lore_components";

	private EntityItemText() {
	}

	/** Loader/range-safe registry access without resolving inherited Entity methods at mixin call sites. */
	public static HolderLookup.Provider registryAccess(Object entity) {
		return entity == null
				? RegistryAccess.EMPTY
				: ((EntityItemTextCarrier) (Object) entity).betterLore$registryAccess();
	}

	public static ItemStack restore(EntityItemTextCarrier source, ItemStack returnedStack) {
		if (returnedStack.isEmpty() || source == null) {
			return returnedStack;
		}

		return restoreOwnedText(
				source.betterLore$currentName(),
				source.betterLore$copyItemTextData(),
				returnedStack,
				source.betterLore$registryAccess()
		);
	}

	/** Pure restore path kept separate for deterministic component round-trip tests. */
	public static ItemStack restoreOwnedText(
			Component currentEntityName,
			CompoundTag retainedCustomData,
			ItemStack returnedStack,
			HolderLookup.Provider registries
	) {
		if (returnedStack.isEmpty() || retainedCustomData == null || retainedCustomData.isEmpty()) {
			return returnedStack;
		}

		ItemStack retainedText = retainedTextStack(retainedCustomData, returnedStack);

		String currentRawName = validRawNameMarkup(returnedStack);
		String rawName = ModDataComponents.getRawNameMarkup(retainedText);
		if ((currentRawName == null || currentRawName.isEmpty())
				&& rawName != null
				&& !rawName.isEmpty()) {
			ParseResult parsedName = LoreMarkupParser.parseName(rawName);
			Component returnedName = returnedStack.get(DataComponents.CUSTOM_NAME);
			if (parsedName.isSuccess()
					&& LoreComponents.equivalentToExistingName(currentEntityName, parsedName.document())
					&& LoreComponents.equivalentToExistingName(returnedName, parsedName.document())) {
				LoreComponents.applyNameTo(returnedStack, rawName, parsedName.document());
			}
		}

		restoreLore(retainedCustomData, retainedText, returnedStack, registries);

		return returnedStack;
	}

	/**
	 * Restores a trusted private item-text snapshot onto a newly-created item.
	 *
	 * <p>Unlike the entity drop path, this path does not require the replacement
	 * item to already expose the entity name. It is used for a mob bucket's
	 * hidden bucket identity after the mob has been released.</p>
	 */
	public static ItemStack restoreStoredText(
			CompoundTag retainedCustomData,
			ItemStack returnedStack,
			HolderLookup.Provider registries
	) {
		if (returnedStack.isEmpty()
				|| retainedCustomData == null
				|| retainedCustomData.isEmpty()) {
			return returnedStack;
		}

		ItemStack retainedText = retainedTextStack(retainedCustomData, returnedStack);
		String rawName = ModDataComponents.getRawNameMarkup(retainedText);
		ParseResult parsedName = LoreMarkupParser.parseName(rawName);
		if (parsedName.isSuccess() && rawName != null && !rawName.isEmpty()) {
			LoreComponents.applyNameTo(returnedStack, rawName, parsedName.document());
		}

		restoreLore(retainedCustomData, retainedText, returnedStack, registries);
		return returnedStack;
	}

	/** Selects the mod-owned nested payload without copying arbitrary entity data to an item. */
	public static CompoundTag onlyBetterLoreData(CompoundTag source) {
		CompoundTag selected = new CompoundTag();
		CompoundTag betterLore = nestedBetterLoreData(source);
		if (!betterLore.isEmpty()) {
			selected.put(AnvilLoreMod.MOD_ID, betterLore.copy());
		}
		return selected;
	}

	/** Selects only the private fields that are valid inside an entity snapshot. */
	public static CompoundTag onlyItemTextSnapshotData(CompoundTag source) {
		CompoundTag selected = onlyBetterLoreData(source);
		if (source == null || source.isEmpty()) {
			return selected;
		}

		Tag foreignLore = source.get(FOREIGN_LORE_COMPONENTS_KEY);
		if (foreignLore != null) {
			selected.put(FOREIGN_LORE_COMPONENTS_KEY, foreignLore.copy());
		}
		return selected;
	}

	public static CompoundTag itemTextDataFromStack(
			ItemStack stack,
			HolderLookup.Provider registries
	) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null || customData.isEmpty()) {
			return new CompoundTag();
		}

		CompoundTag selected = onlyBetterLoreData(customData.copyTag());
		if (selected.isEmpty()) {
			return selected;
		}

		ItemStack retainedText = retainedTextStack(selected, stack);
		String rawName = ModDataComponents.getRawNameMarkup(retainedText);
		ParseResult parsedName = LoreMarkupParser.parseName(rawName);
		if (!parsedName.isSuccess()
				|| !LoreComponents.equivalentToExistingName(
						stack.get(DataComponents.CUSTOM_NAME),
						parsedName.document()
				)) {
			ModDataComponents.removeRawNameMarkup(retainedText);
		}

		String rawLore = ModDataComponents.getRawLoreMarkup(retainedText);
		ParseResult parsedLore = LoreMarkupParser.parse(rawLore);
		if (!ModDataComponents.hasCurrentLoreOwnership(stack)
				|| !parsedLore.isSuccess()
				|| !LoreComponents.equivalentToExistingLore(stack, parsedLore.document())) {
			ModDataComponents.removeRawLoreMarkup(retainedText);
		}

		String retainedName = ModDataComponents.getRawNameMarkup(retainedText);
		String retainedLore = ModDataComponents.getRawLoreMarkup(retainedText);
		boolean hasRetainedName = retainedName != null && !retainedName.isEmpty();
		boolean hasRetainedLore = retainedLore != null && !retainedLore.isEmpty();
		if (!hasRetainedName && !hasRetainedLore) {
			return new CompoundTag();
		}

		CompoundTag snapshot = onlyBetterLoreData(
				retainedText.get(DataComponents.CUSTOM_DATA).copyTag()
		);
		List<Component> foreignLore = hasRetainedLore
				? LoreComponents.foreignComponents(stack)
				: visibleLore(stack);
		if (!foreignLore.isEmpty()) {
			snapshot.put(
					FOREIGN_LORE_COMPONENTS_KEY,
					ComponentListNbtCodec.encode(foreignLore, registries)
			);
		}
		return snapshot;
	}

	/** Reads the compatibility snapshot used before entities retained CUSTOM_DATA themselves. */
	public static CompoundTag readEntitySnapshot(CompoundTag entityData) {
		if (entityData == null || entityData.isEmpty()) {
			return new CompoundTag();
		}
		//? if >=1.21.5 {
		return entityData.getCompoundOrEmpty(ENTITY_SNAPSHOT_KEY).copy();
		//? } else {
		return entityData.contains(ENTITY_SNAPSHOT_KEY, Tag.TAG_COMPOUND)
				? entityData.getCompound(ENTITY_SNAPSHOT_KEY).copy()
				: new CompoundTag();
		//? }
	}

	/** Persists the compatibility snapshot without exposing it as vanilla item data. */
	public static void writeEntitySnapshot(CompoundTag selected, CompoundTag entityData) {
		entityData.remove(ENTITY_SNAPSHOT_KEY);
		CompoundTag snapshot = onlyItemTextSnapshotData(selected);
		if (!snapshot.isEmpty()) {
			entityData.put(ENTITY_SNAPSHOT_KEY, snapshot);
		}
	}

	private static void restoreLore(
			CompoundTag retainedCustomData,
			ItemStack retainedText,
			ItemStack returnedStack,
			HolderLookup.Provider registries
	) {
		String currentOwnedLore = validOwnedLoreMarkup(returnedStack);
		String retainedOwnedLore = ModDataComponents.getRawLoreMarkup(retainedText);
		ParseResult parsedRetainedLore = LoreMarkupParser.parse(retainedOwnedLore);
		if (!ModDataComponents.hasCurrentLoreOwnership(retainedText)
				|| !parsedRetainedLore.isSuccess()) {
			retainedOwnedLore = "";
		}

		String selectedOwnedLore = PlacedItemTextStorage.selectOwnedLore(
				currentOwnedLore,
				retainedOwnedLore
		);
		List<Component> currentForeign = currentOwnedLore.isEmpty()
				? visibleLore(returnedStack)
				: LoreComponents.foreignComponents(returnedStack);
		PlacedItemTextStorage.LoreRestorePlan plan = PlacedItemTextStorage.planLoreRestore(
				currentForeign,
				readForeignLore(retainedCustomData, registries),
				"",
				selectedOwnedLore
		);
		if (plan == null) {
			return;
		}

		setVisibleLore(returnedStack, plan.foreignLore());
		if (plan.rawOwnedLore().isEmpty()) {
			ModDataComponents.removeRawLoreMarkup(returnedStack);
		} else {
			LoreComponents.applyTo(
					returnedStack,
					plan.rawOwnedLore(),
					plan.ownedDocument()
			);
		}
	}

	private static String validRawNameMarkup(ItemStack stack) {
		String rawName = ModDataComponents.getRawNameMarkup(stack);
		ParseResult parsed = LoreMarkupParser.parseName(rawName);
		return parsed.isSuccess()
				&& LoreComponents.equivalentToExistingName(
						stack.get(DataComponents.CUSTOM_NAME),
						parsed.document()
				)
				? rawName
				: null;
	}

	private static String validOwnedLoreMarkup(ItemStack stack) {
		String rawLore = ModDataComponents.getRawLoreMarkup(stack);
		ParseResult parsed = LoreMarkupParser.parse(rawLore);
		return ModDataComponents.hasCurrentLoreOwnership(stack)
				&& parsed.isSuccess()
				&& LoreComponents.equivalentToExistingLore(stack, parsed.document())
				? rawLore
				: "";
	}

	private static List<Component> readForeignLore(
			CompoundTag snapshot,
			HolderLookup.Provider registries
	) {
		Tag encoded = snapshot.get(FOREIGN_LORE_COMPONENTS_KEY);
		if (encoded == null) {
			return List.of();
		}
		try {
			return ComponentListNbtCodec.decode(encoded, registries);
		} catch (RuntimeException ignored) {
			return List.of();
		}
	}

	private static List<Component> visibleLore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : List.copyOf(lore.lines());
	}

	private static void setVisibleLore(ItemStack stack, List<Component> lines) {
		if (lines.isEmpty()) {
			stack.remove(DataComponents.LORE);
		} else {
			stack.set(DataComponents.LORE, new ItemLore(lines));
		}
	}

	private static ItemStack retainedTextStack(CompoundTag selected, ItemStack returnedStack) {
		ItemStack retained = new ItemStack(returnedStack.getItem());
		retained.set(DataComponents.CUSTOM_DATA, CustomData.of(selected.copy()));
		return retained;
	}

	private static CompoundTag nestedBetterLoreData(CompoundTag source) {
		if (source == null || source.isEmpty()) {
			return new CompoundTag();
		}
		//? if >=1.21.5 {
		return source.getCompoundOrEmpty(AnvilLoreMod.MOD_ID);
		//? } else {
		return source.contains(AnvilLoreMod.MOD_ID, Tag.TAG_COMPOUND)
				? source.getCompound(AnvilLoreMod.MOD_ID)
				: new CompoundTag();
		//? }
	}
}
