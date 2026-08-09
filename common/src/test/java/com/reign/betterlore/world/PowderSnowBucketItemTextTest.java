package com.reign.betterlore.world;

import com.reign.betterlore.ModDataComponents;
import com.reign.betterlore.lore.LoreComponents;
import com.reign.betterlore.lore.LoreMarkupDecompiler;
import com.reign.betterlore.lore.LoreMarkupParser;
import com.reign.betterlore.lore.ParseResult;
import com.reign.betterlore.test.MinecraftTestBootstrap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression coverage for SolidBucketItem consuming its source before replacement. */
class PowderSnowBucketItemTextTest {
	private static final String RAW_NAME =
			"<gr #d8f3ff #8bbfd9><b>Powder reserve</b></gr>";
	private static final String RAW_LORE =
			"<c #9ad8f4>Retained through placement</c>";

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureCodecRegistries();
	}

	@Test
	void placementSnapshotRestoresEmptyBucketAndPickupRestoresFilledBucket() {
		ItemStack held = styledPowderSnowBucket();
		ItemStack preUseSnapshot = held.copy();

		// BlockItem.place consumes the stack before SolidBucketItem asks for the
		// empty replacement. The ordinary BucketItem return hook consequently sees
		// an empty source and cannot carry its text forward.
		held.setCount(0);
		assertTrue(held.isEmpty());
		ItemStack returnedEmpty = new ItemStack(Items.BUCKET);
		BucketItemText.onEmptiedBucket(held, returnedEmpty, RegistryAccess.EMPTY);
		assertUnstyled(returnedEmpty);

		// SolidBucketItemTextMixin restores from the snapshot taken before place().
		BucketItemText.onEmptiedBucket(
				preUseSnapshot,
				returnedEmpty,
				RegistryAccess.EMPTY
		);
		assertExactText(returnedEmpty);
		assertEquals(
				preUseSnapshot.get(DataComponents.CUSTOM_NAME),
				returnedEmpty.get(DataComponents.CUSTOM_NAME)
		);
		assertEquals(
				preUseSnapshot.get(DataComponents.LORE),
				returnedEmpty.get(DataComponents.LORE)
		);

		// Powder-snow pickup passes this restored bucket through createFilledResult.
		ItemStack refilled = new ItemStack(Items.POWDER_SNOW_BUCKET);
		BucketItemText.onFilledBucket(returnedEmpty, refilled, RegistryAccess.EMPTY);
		assertExactText(refilled);
		assertEquals(
				returnedEmpty.get(DataComponents.CUSTOM_NAME),
				refilled.get(DataComponents.CUSTOM_NAME)
		);
		assertEquals(
				returnedEmpty.get(DataComponents.LORE),
				refilled.get(DataComponents.LORE)
		);
	}

	private static ItemStack styledPowderSnowBucket() {
		ItemStack stack = new ItemStack(Items.POWDER_SNOW_BUCKET);
		ParseResult name = LoreMarkupParser.parseName(RAW_NAME);
		ParseResult lore = LoreMarkupParser.parse(RAW_LORE);
		assertTrue(name.isSuccess());
		assertTrue(lore.isSuccess());
		LoreComponents.applyNameTo(stack, RAW_NAME, name.document());
		LoreComponents.applyTo(stack, RAW_LORE, lore.document());
		return stack;
	}

	private static void assertUnstyled(ItemStack stack) {
		assertNull(stack.get(DataComponents.CUSTOM_NAME));
		ItemLore lore = stack.get(DataComponents.LORE);
		assertTrue(lore == null || lore.lines().isEmpty());
		assertNull(ModDataComponents.getRawNameMarkup(stack));
		assertNull(ModDataComponents.getRawLoreMarkup(stack));
		assertFalse(ModDataComponents.hasCurrentLoreOwnership(stack));
	}

	private static void assertExactText(ItemStack stack) {
		assertEquals(
				LoreMarkupParser.toPreferredNameMarkup(RAW_NAME),
				LoreMarkupDecompiler.toSafeNameMarkup(stack)
		);
		assertEquals(
				LoreMarkupParser.toPreferredMarkup(RAW_LORE),
				LoreMarkupDecompiler.toSafeOwnedLoreMarkup(stack)
		);
		assertTrue(ModDataComponents.hasCurrentLoreOwnership(stack));
	}
}
