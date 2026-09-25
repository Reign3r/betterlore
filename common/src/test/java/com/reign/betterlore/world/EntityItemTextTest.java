package com.reign.betterlore.world;

import com.reign.betterlore.ModDataComponents;
import com.reign.betterlore.lore.LoreComponents;
import com.reign.betterlore.lore.LegacyItemFixtures;
import com.reign.betterlore.lore.LoreMarkupDecompiler;
import com.reign.betterlore.lore.LoreMarkupParser;
import com.reign.betterlore.lore.ParseResult;
import com.reign.betterlore.test.MinecraftTestBootstrap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityItemTextTest {
	private static final String RAW_NAME =
			"<gr #7a683f #2a8456><b><i>Craze</i></b></gr>";
	private static final String RAW_LORE = "<c #3f9f7a>Kept through placement</c>";
	private static final String CURRENT_NAME = "<c #b75534><underlined>Newer drop</underlined></c>";
	private static final String CURRENT_LORE = "<b>Newer owned lore</b>";

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureCodecRegistries();
	}

	@Test
	void entityRoundTripPreservesExactGradientSourceAndOwnedLore() {
		for (Item item : new Item[] {
				Items.MINECART,
				Items.OAK_BOAT,
				Items.ARMOR_STAND,
				Items.COD_BUCKET
		}) {
			ItemStack original = styledItem(item);
			Component entityName = original.get(DataComponents.CUSTOM_NAME);
			CompoundTag retained = retainedTextData(original);

			ItemStack withoutPayload = new ItemStack(item);
			withoutPayload.set(DataComponents.CUSTOM_NAME, entityName);
			assertTrue(LoreMarkupDecompiler.toSafeNameMarkup(withoutPayload).contains("</c><c "));

			ItemStack returned = new ItemStack(item);
			returned.set(DataComponents.CUSTOM_NAME, entityName);
			EntityItemText.restoreOwnedText(
					entityName,
					retained,
					returned,
					RegistryAccess.EMPTY
			);

			assertEquals(
					LoreMarkupParser.toPreferredNameMarkup(RAW_NAME),
					LoreMarkupDecompiler.toSafeNameMarkup(returned)
			);
			assertEquals(
					LoreMarkupParser.toPreferredMarkup(RAW_LORE),
					LoreMarkupDecompiler.toSafeOwnedLoreMarkup(returned)
			);
			assertTrue(ModDataComponents.hasCurrentLoreOwnership(returned));
			assertFalse(LoreMarkupDecompiler.toSafeNameMarkup(returned).contains("</c><c "));
		}
	}

	@Test
	void newerReturnedItemNameDoesNotGetOverwrittenByStaleEntityData() {
		ItemStack original = styledItem(Items.ARMOR_STAND);
		CompoundTag retained = retainedTextData(original);
		Component entityName = original.get(DataComponents.CUSTOM_NAME);
		Component renamed = Component.literal("Renamed later");

		ItemStack returned = new ItemStack(Items.ARMOR_STAND);
		returned.set(DataComponents.CUSTOM_NAME, renamed);
		EntityItemText.restoreOwnedText(
				entityName,
				retained,
				returned,
				RegistryAccess.EMPTY
		);

		assertEquals(renamed, returned.get(DataComponents.CUSTOM_NAME));
		assertEquals("Renamed later", LoreMarkupDecompiler.toSafeNameMarkup(returned));
		assertNull(ModDataComponents.getRawNameMarkup(returned));
		assertEquals(
				LoreMarkupParser.toPreferredMarkup(RAW_LORE),
				LoreMarkupDecompiler.toSafeOwnedLoreMarkup(returned)
		);
	}

	@Test
	void newerReturnedItemSourcesAndFutureDataWinOverEntitySnapshot() {
		ItemStack original = styledItem(Items.OAK_BOAT);
		CompoundTag retained = retainedTextData(original);
		Component entityName = original.get(DataComponents.CUSTOM_NAME);
		ItemStack returned = styledItem(Items.OAK_BOAT, CURRENT_NAME, CURRENT_LORE, true);
		Component returnedName = returned.get(DataComponents.CUSTOM_NAME);
		CompoundTag before = returned.get(DataComponents.CUSTOM_DATA).copyTag();

		EntityItemText.restoreOwnedText(
				entityName,
				retained,
				returned,
				RegistryAccess.EMPTY
		);

		assertEquals(returnedName, returned.get(DataComponents.CUSTOM_NAME));
		assertEquals(
				LoreMarkupParser.toPreferredNameMarkup(CURRENT_NAME),
				LoreMarkupDecompiler.toSafeNameMarkup(returned)
		);
		assertEquals(
				LoreMarkupParser.toPreferredMarkup(CURRENT_LORE),
				LoreMarkupDecompiler.toSafeOwnedLoreMarkup(returned)
		);
		assertEquals(before, returned.get(DataComponents.CUSTOM_DATA).copyTag());
	}

	@Test
	void selectingEntityTextNeverCopiesUnrelatedCustomData() {
		CompoundTag source = retainedTextData(styledItem(Items.ARMOR_STAND));
		source.putString("unrelated_entity_data", "must-not-reach-item");

		CompoundTag selected = EntityItemText.onlyBetterLoreData(source);

		assertTrue(selected.contains("better_lore"));
		assertFalse(selected.contains("unrelated_entity_data"));
	}

	@Test
	void staleOwnedLoreMetadataIsNotResurrectedAfterAnotherModReplacesLore() {
		ItemStack source = styledItem(Items.MINECART);
		Component foreignReplacement = Component.literal("Replacement from another mod");
		source.set(DataComponents.LORE, new ItemLore(java.util.List.of(foreignReplacement)));
		CompoundTag retained = EntityItemText.itemTextDataFromStack(
				source,
				RegistryAccess.EMPTY
		);

		ItemStack returned = new ItemStack(Items.MINECART);
		returned.set(DataComponents.CUSTOM_NAME, source.get(DataComponents.CUSTOM_NAME));
		EntityItemText.restoreOwnedText(
				source.get(DataComponents.CUSTOM_NAME),
				retained,
				returned,
				RegistryAccess.EMPTY
		);

		assertEquals("", LoreMarkupDecompiler.toSafeOwnedLoreMarkup(returned));
		assertFalse(ModDataComponents.hasCurrentLoreOwnership(returned));
		assertEquals(List.of(foreignReplacement), LoreComponents.foreignComponents(returned));
	}

	@Test
	void staleRawNameMetadataIsNotCapturedAfterAnotherModRenamesTheItem() {
		ItemStack source = styledItem(Items.ARMOR_STAND);
		Component replacement = Component.literal("Replacement from another mod");
		source.set(DataComponents.CUSTOM_NAME, replacement);
		CompoundTag retained = retainedTextData(source);

		ItemStack returned = new ItemStack(Items.ARMOR_STAND);
		returned.set(DataComponents.CUSTOM_NAME, replacement);
		EntityItemText.restoreOwnedText(
				replacement,
				retained,
				returned,
				RegistryAccess.EMPTY
		);

		assertEquals(replacement, returned.get(DataComponents.CUSTOM_NAME));
		assertNull(ModDataComponents.getRawNameMarkup(returned));
		assertEquals(
				LoreMarkupParser.toPreferredMarkup(RAW_LORE),
				LoreMarkupDecompiler.toSafeOwnedLoreMarkup(returned)
		);
	}

	@Test
	void sourceForeignLoreRoundTripsExactlyAndIsNotDuplicatedOnTheReturnedItem() {
		ItemStack source = styledItem(Items.MINECART);
		Component foreign = Component.translatable("container.anvil")
				.append(Component.literal(" exact").withStyle(ChatFormatting.GOLD))
				.withStyle(style -> style.withInsertion("foreign:metadata"));
		ItemLore ownedLore = source.get(DataComponents.LORE);
		List<Component> combined = new ArrayList<>();
		combined.add(foreign);
		combined.addAll(ownedLore.lines());
		source.set(DataComponents.LORE, new ItemLore(combined));

		Component entityName = source.get(DataComponents.CUSTOM_NAME);
		CompoundTag retained = retainedTextData(source);

		ItemStack returned = new ItemStack(Items.MINECART);
		returned.set(DataComponents.CUSTOM_NAME, entityName);
		EntityItemText.restoreOwnedText(
				entityName,
				retained,
				returned,
				RegistryAccess.EMPTY
		);
		assertEquals(List.of(foreign), LoreComponents.foreignComponents(returned));

		ItemStack alreadyContainsForeign = new ItemStack(Items.MINECART);
		alreadyContainsForeign.set(DataComponents.CUSTOM_NAME, entityName);
		alreadyContainsForeign.set(DataComponents.LORE, new ItemLore(List.of(foreign)));
		EntityItemText.restoreOwnedText(
				entityName,
				retained,
				alreadyContainsForeign,
				RegistryAccess.EMPTY
		);
		assertEquals(
				List.of(foreign),
				LoreComponents.foreignComponents(alreadyContainsForeign)
		);
	}

	@Test
	void foreignLoreAloneDoesNotCreateAnEntitySnapshot() {
		ItemStack source = new ItemStack(Items.MINECART);
		source.set(
				DataComponents.LORE,
				new ItemLore(List.of(Component.literal("Foreign-only lore")))
		);
		CompoundTag root = new CompoundTag();
		CompoundTag futureBetterLoreData = new CompoundTag();
		futureBetterLoreData.putString("future_key", "not item-text ownership");
		root.put("better_lore", futureBetterLoreData);
		source.set(DataComponents.CUSTOM_DATA, CustomData.of(root));

		assertTrue(
				EntityItemText.itemTextDataFromStack(source, RegistryAccess.EMPTY).isEmpty()
		);
	}

	@Test
	void restorePreservesForeignLoreAndUnrelatedTargetCustomData() {
		ItemStack original = styledItem(Items.MINECART);
		Component entityName = original.get(DataComponents.CUSTOM_NAME);
		CompoundTag retained = retainedTextData(original);
		Component foreign = Component.literal("Foreign mod line");

		ItemStack returned = new ItemStack(Items.MINECART);
		returned.set(DataComponents.CUSTOM_NAME, entityName);
		returned.set(DataComponents.LORE, new ItemLore(java.util.List.of(foreign)));
		CompoundTag unrelated = new CompoundTag();
		unrelated.putString("unrelated_item_data", "kept");
		returned.set(DataComponents.CUSTOM_DATA, CustomData.of(unrelated));

		EntityItemText.restoreOwnedText(
				entityName,
				retained,
				returned,
				RegistryAccess.EMPTY
		);

		assertEquals(java.util.List.of(foreign), LoreComponents.foreignComponents(returned));
		assertTrue(
				returned.get(DataComponents.CUSTOM_DATA)
						.copyTag()
						.contains("unrelated_item_data")
		);
	}

	@Test
	void entitySnapshotRoundTripsAndEmptyWriteClearsStaleData() {
		CompoundTag retained = retainedTextData(styledItem(Items.COD_BUCKET));
		CompoundTag entityData = new CompoundTag();

		EntityItemText.writeEntitySnapshot(retained, entityData);
		assertEquals(retained, EntityItemText.readEntitySnapshot(entityData));

		EntityItemText.writeEntitySnapshot(new CompoundTag(), entityData);
		assertTrue(EntityItemText.readEntitySnapshot(entityData).isEmpty());
	}

	private static ItemStack styledItem(Item item) {
		return styledItem(item, RAW_NAME, RAW_LORE, false);
	}

	@Test
	void capturedLegacyItemsMigrateBeforeEntityAndBucketTransfers() {
		for (LegacyItemFixtures.Item fixture : LegacyItemFixtures.items()) {
			ItemStack original = new ItemStack(Items.MINECART);
			original.set(DataComponents.LORE, new ItemLore(fixture.lore()));
			if (fixture.name() != null) original.set(DataComponents.CUSTOM_NAME, fixture.name());
			ModDataComponents.setRawNameMarkup(original, fixture.rawName());
			if (fixture.currentOwnership()) ModDataComponents.setOwnedLoreMarkup(original, fixture.rawLore());
			else ModDataComponents.setRawLoreMarkup(original, fixture.rawLore());
			String expectedLore = LoreMarkupDecompiler.toSafeOwnedLoreMarkup(original);
			String expectedName = LoreMarkupDecompiler.matchingStoredNameMarkup(fixture.rawName(), fixture.name());
			CompoundTag retained = EntityItemText.itemTextDataFromStack(original, RegistryAccess.EMPTY);
			assertFalse(retained.isEmpty(), fixture.label());
			for (boolean bucket : new boolean[] {false, true}) {
				ItemStack returned = new ItemStack(Items.MINECART);
				if (bucket) {
					EntityItemText.restoreStoredText(retained, returned, RegistryAccess.EMPTY);
				} else {
					if (fixture.name() != null) returned.set(DataComponents.CUSTOM_NAME, fixture.name());
					EntityItemText.restoreOwnedText(fixture.name(), retained, returned, RegistryAccess.EMPTY);
				}
				assertEquals(expectedLore, LoreMarkupDecompiler.toSafeOwnedLoreMarkup(returned), fixture.label());
				assertTrue(LoreComponents.equivalentToExistingLore(returned, LoreMarkupParser.parse(expectedLore).document()));
				assertEquals(expectedName, ModDataComponents.getRawNameMarkup(returned), fixture.label());
			}
			assertEquals(fixture.lore(), original.get(DataComponents.LORE).lines());
		}
	}

	private static ItemStack styledItem(
			Item item,
			String rawName,
			String rawLore,
			boolean includeFutureBetterLoreData
	) {
		ItemStack stack = new ItemStack(item);
		if (includeFutureBetterLoreData) {
			CompoundTag root = new CompoundTag();
			CompoundTag betterLore = new CompoundTag();
			betterLore.putString("future_key", "must-survive");
			root.put("better_lore", betterLore);
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
		}
		ParseResult name = LoreMarkupParser.parseName(rawName);
		ParseResult lore = LoreMarkupParser.parse(rawLore);
		assertTrue(name.isSuccess());
		assertTrue(lore.isSuccess());
		LoreComponents.applyNameTo(stack, rawName, name.document());
		LoreComponents.applyTo(stack, rawLore, lore.document());
		return stack;
	}

	private static CompoundTag retainedTextData(ItemStack stack) {
		return EntityItemText.itemTextDataFromStack(stack, RegistryAccess.EMPTY);
	}

}
