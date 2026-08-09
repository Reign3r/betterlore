package com.reign.betterlore.world;

import com.reign.betterlore.ModDataComponents;
import com.reign.betterlore.lore.LoreComponents;
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

class BucketItemTextTest {
	private static final String BUCKET_NAME = "<c #8a5a32><b>Utility bucket</b></c>";
	private static final String BUCKET_LORE = "<c #65a9d8>Belongs to the bucket</c>";
	private static final String MOB_NAME =
			"<gr #7a683f #2a8456><b><i>Craze</i></b></gr>";
	private static final String MOB_LORE = "<c #59c9a5>Belongs to the axolotl</c>";

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureCodecRegistries();
	}

	@Test
	void regularFluidMilkAndPowderSnowBucketsKeepExactTextBothDirections() {
		for (Item filledItem : new Item[] {
				Items.WATER_BUCKET,
				Items.LAVA_BUCKET,
				Items.MILK_BUCKET,
				Items.POWDER_SNOW_BUCKET
		}) {
			ItemStack empty = styled(Items.BUCKET, BUCKET_NAME, BUCKET_LORE);
			ItemStack filled = new ItemStack(filledItem);
			BucketItemText.onFilledBucket(empty, filled, RegistryAccess.EMPTY);
			assertExactText(filled, BUCKET_NAME, BUCKET_LORE);

			ItemStack returnedEmpty = new ItemStack(Items.BUCKET);
			BucketItemText.onEmptiedBucket(filled, returnedEmpty, RegistryAccess.EMPTY);
			assertExactText(returnedEmpty, BUCKET_NAME, BUCKET_LORE);
		}
	}

	@Test
	void mobBucketDisplaysMobTextButRestoresItsPrivateBucketIdentityOnRelease() {
		ItemStack waterBucket = styled(Items.WATER_BUCKET, BUCKET_NAME, BUCKET_LORE);
		ItemStack mobBucket = styled(Items.AXOLOTL_BUCKET, MOB_NAME, MOB_LORE);

		BucketItemText.onFilledBucket(waterBucket, mobBucket, RegistryAccess.EMPTY);

		assertExactText(mobBucket, MOB_NAME, MOB_LORE);
		assertFalse(BucketItemText.readBucketIdentity(mobBucket).isEmpty());

		ItemStack returnedEmpty = new ItemStack(Items.BUCKET);
		BucketItemText.onEmptiedBucket(mobBucket, returnedEmpty, RegistryAccess.EMPTY);
		assertExactText(returnedEmpty, BUCKET_NAME, BUCKET_LORE);
	}

	@Test
	void mobReleaseRefillAndRecaptureKeepMobAndBucketIdentitiesSeparate() {
		ItemStack waterBucket = styled(Items.WATER_BUCKET, BUCKET_NAME, BUCKET_LORE);
		ItemStack firstMobBucket = styled(Items.AXOLOTL_BUCKET, MOB_NAME, MOB_LORE);
		BucketItemText.onFilledBucket(waterBucket, firstMobBucket, RegistryAccess.EMPTY);
		assertExactText(firstMobBucket, MOB_NAME, MOB_LORE);
		assertFalse(BucketItemText.readBucketIdentity(firstMobBucket).isEmpty());

		// Entity.applyComponentsFromItemStack retains the mod-owned mob snapshot,
		// but the container-only identity must not become entity state.
		CompoundTag spawnedEntityData = firstMobBucket
				.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
				.copyTag();
		BucketItemText.removePrivateBucketIdentity(spawnedEntityData);
		ItemStack entityDataProbe = new ItemStack(Items.AXOLOTL_BUCKET);
		entityDataProbe.set(DataComponents.CUSTOM_DATA, CustomData.of(spawnedEntityData));
		assertTrue(BucketItemText.readBucketIdentity(entityDataProbe).isEmpty());
		CompoundTag mobSnapshot = EntityItemText.itemTextDataFromStack(
				firstMobBucket,
				RegistryAccess.EMPTY
		);

		ItemStack returnedEmpty = new ItemStack(Items.BUCKET);
		BucketItemText.onEmptiedBucket(firstMobBucket, returnedEmpty, RegistryAccess.EMPTY);
		// Vanilla next reaches ItemUtils.createFilledResult with the original mob
		// bucket and this already-restored empty result.
		BucketItemText.onFilledBucket(firstMobBucket, returnedEmpty, RegistryAccess.EMPTY);
		assertExactText(returnedEmpty, BUCKET_NAME, BUCKET_LORE);

		ItemStack refilledWater = new ItemStack(Items.WATER_BUCKET);
		BucketItemText.onFilledBucket(returnedEmpty, refilledWater, RegistryAccess.EMPTY);
		assertExactText(refilledWater, BUCKET_NAME, BUCKET_LORE);

		// saveDefaultDataToBucketTag first copies the entity's visible name; the
		// Better Lore hook then restores the exact mob snapshot. Only after that
		// does ItemUtils attach the distinct source-bucket identity privately.
		ItemStack recapturedMob = new ItemStack(Items.AXOLOTL_BUCKET);
		Component mobName = firstMobBucket.get(DataComponents.CUSTOM_NAME);
		recapturedMob.set(DataComponents.CUSTOM_NAME, mobName);
		EntityItemText.restoreOwnedText(
				mobName,
				mobSnapshot,
				recapturedMob,
				RegistryAccess.EMPTY
		);
		BucketItemText.onFilledBucket(refilledWater, recapturedMob, RegistryAccess.EMPTY);
		assertExactText(recapturedMob, MOB_NAME, MOB_LORE);

		ItemStack secondReturnedEmpty = new ItemStack(Items.BUCKET);
		BucketItemText.onEmptiedBucket(
				recapturedMob,
				secondReturnedEmpty,
				RegistryAccess.EMPTY
		);
		BucketItemText.onFilledBucket(
				recapturedMob,
				secondReturnedEmpty,
				RegistryAccess.EMPTY
		);
		assertExactText(secondReturnedEmpty, BUCKET_NAME, BUCKET_LORE);
	}

	@Test
	void mobBucketWithoutPrivateIdentityDoesNotLeakMobTextOntoEmptyBucket() {
		ItemStack mobBucket = styled(Items.COD_BUCKET, MOB_NAME, MOB_LORE);
		ItemStack returnedEmpty = new ItemStack(Items.BUCKET);

		BucketItemText.onEmptiedBucket(mobBucket, returnedEmpty, RegistryAccess.EMPTY);

		assertNull(returnedEmpty.get(DataComponents.CUSTOM_NAME));
		ItemLore returnedLore = returnedEmpty.get(DataComponents.LORE);
		assertTrue(returnedLore == null || returnedLore.lines().isEmpty());
		assertNull(ModDataComponents.getRawNameMarkup(returnedEmpty));
		assertFalse(ModDataComponents.hasCurrentLoreOwnership(returnedEmpty));
	}

	@Test
	void formattedNameTagSnapshotTransfersExactGradientAndLoreToMobBucket() {
		ItemStack nameTag = styled(Items.NAME_TAG, MOB_NAME, MOB_LORE);
		Component mobName = nameTag.get(DataComponents.CUSTOM_NAME);
		ItemStack capturedMob = new ItemStack(Items.AXOLOTL_BUCKET);
		capturedMob.set(DataComponents.CUSTOM_NAME, mobName);

		EntityItemText.restoreOwnedText(
				mobName,
				EntityItemText.itemTextDataFromStack(nameTag, RegistryAccess.EMPTY),
				capturedMob,
				RegistryAccess.EMPTY
		);

		assertExactText(capturedMob, MOB_NAME, MOB_LORE);
		assertFalse(LoreMarkupDecompiler.toSafeNameMarkup(capturedMob).contains("</c><c "));
	}

	@Test
	void hiddenBucketIdentityPreservesForeignLoreWithoutMakingItEditable() {
		ItemStack waterBucket = styled(Items.WATER_BUCKET, BUCKET_NAME, BUCKET_LORE);
		Component foreign = Component.translatable("container.anvil")
				.append(Component.literal(" metadata").withStyle(ChatFormatting.GOLD));
		List<Component> combined = new ArrayList<>();
		combined.add(foreign);
		combined.addAll(waterBucket.get(DataComponents.LORE).lines());
		waterBucket.set(DataComponents.LORE, new ItemLore(combined));

		ItemStack mobBucket = styled(Items.COD_BUCKET, MOB_NAME, MOB_LORE);
		BucketItemText.onFilledBucket(waterBucket, mobBucket, RegistryAccess.EMPTY);
		ItemStack returnedEmpty = new ItemStack(Items.BUCKET);
		BucketItemText.onEmptiedBucket(mobBucket, returnedEmpty, RegistryAccess.EMPTY);

		assertEquals(List.of(foreign), LoreComponents.foreignComponents(returnedEmpty));
		assertEquals(
				LoreMarkupParser.toPreferredMarkup(BUCKET_LORE),
				LoreMarkupDecompiler.toSafeOwnedLoreMarkup(returnedEmpty)
		);
	}

	@Test
	void hiddenBucketIdentityPreservesPlainNameAndForeignOnlyLoreExactly() {
		Component plainName = Component.translatable("item.minecraft.water_bucket")
				.append(Component.literal(" exact").withStyle(ChatFormatting.AQUA))
				.withStyle(style -> style.withInsertion("plain-name-metadata"));
		Component firstForeign = Component.translatable("container.anvil")
				.withStyle(ChatFormatting.GOLD);
		Component secondForeign = Component.literal("Foreign-only line")
				.withStyle(style -> style.withItalic(false).withInsertion("foreign-lore-metadata"));
		List<Component> foreignLore = List.of(firstForeign, secondForeign);
		ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET);
		waterBucket.set(DataComponents.CUSTOM_NAME, plainName);
		waterBucket.set(DataComponents.LORE, new ItemLore(foreignLore));
		ItemStack mobBucket = styled(Items.AXOLOTL_BUCKET, MOB_NAME, MOB_LORE);

		BucketItemText.onFilledBucket(waterBucket, mobBucket, RegistryAccess.EMPTY);
		assertExactText(mobBucket, MOB_NAME, MOB_LORE);

		ItemStack returnedEmpty = new ItemStack(Items.BUCKET);
		BucketItemText.onEmptiedBucket(mobBucket, returnedEmpty, RegistryAccess.EMPTY);

		assertEquals(plainName, returnedEmpty.get(DataComponents.CUSTOM_NAME));
		assertEquals(foreignLore, returnedEmpty.get(DataComponents.LORE).lines());
		assertEquals(foreignLore, LoreComponents.foreignComponents(returnedEmpty));
		assertNull(ModDataComponents.getRawNameMarkup(returnedEmpty));
		assertNull(ModDataComponents.getRawLoreMarkup(returnedEmpty));
		assertFalse(ModDataComponents.hasCurrentLoreOwnership(returnedEmpty));
	}

	private static ItemStack styled(Item item, String rawName, String rawLore) {
		ItemStack stack = new ItemStack(item);
		ParseResult name = LoreMarkupParser.parseName(rawName);
		ParseResult lore = LoreMarkupParser.parse(rawLore);
		assertTrue(name.isSuccess());
		assertTrue(lore.isSuccess());
		LoreComponents.applyNameTo(stack, rawName, name.document());
		LoreComponents.applyTo(stack, rawLore, lore.document());
		return stack;
	}

	private static void assertExactText(ItemStack stack, String rawName, String rawLore) {
		assertEquals(
				LoreMarkupParser.toPreferredNameMarkup(rawName),
				LoreMarkupDecompiler.toSafeNameMarkup(stack)
		);
		assertEquals(
				LoreMarkupParser.toPreferredMarkup(rawLore),
				LoreMarkupDecompiler.toSafeOwnedLoreMarkup(stack)
		);
		assertTrue(ModDataComponents.hasCurrentLoreOwnership(stack));
	}
}
