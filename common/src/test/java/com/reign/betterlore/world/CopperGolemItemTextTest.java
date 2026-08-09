package com.reign.betterlore.world;

import com.reign.betterlore.ModDataComponents;
import com.reign.betterlore.access.EntityItemTextCarrier;
import com.reign.betterlore.lore.LoreComponents;
import com.reign.betterlore.lore.LoreMarkupDecompiler;
import com.reign.betterlore.lore.LoreMarkupParser;
import com.reign.betterlore.lore.ParseResult;
import com.reign.betterlore.test.MinecraftTestBootstrap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopperGolemItemTextTest {
	private static final String RAW_NAME =
			"<gr #7a683f #2a8456><b><i>Copper Friend</i></b></gr>";
	private static final String RAW_LORE =
			"<c #b87333>Remembered through statue form</c>";

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureCodecRegistries();
	}

	@Test
	void golemStatueGolemRoundTripPreservesExactOwnedSource() {
		ItemStack original = new ItemStack(Items.STONE);
		ParseResult parsedName = LoreMarkupParser.parseName(RAW_NAME);
		ParseResult parsedLore = LoreMarkupParser.parse(RAW_LORE);
		assertTrue(parsedName.isSuccess());
		assertTrue(parsedLore.isSuccess());
		LoreComponents.applyNameTo(original, RAW_NAME, parsedName.document());
		LoreComponents.applyTo(original, RAW_LORE, parsedLore.document());

		CompoundTag entitySnapshot = EntityItemText.itemTextDataFromStack(
				original,
				RegistryAccess.EMPTY
		);
		FakeCarrier golem = new FakeCarrier(
				original.get(DataComponents.CUSTOM_NAME),
				entitySnapshot
		);
		ItemStack statue = CopperGolemItemText.copyFromGolem(
				golem,
				new ItemStack(Items.STONE)
		);

		assertEquals(
				LoreMarkupParser.toPreferredNameMarkup(RAW_NAME),
				LoreMarkupDecompiler.toSafeNameMarkup(statue)
		);
		assertEquals(
				LoreMarkupParser.toPreferredMarkup(RAW_LORE),
				LoreMarkupDecompiler.toSafeOwnedLoreMarkup(statue)
		);
		assertEquals(
				entitySnapshot,
				CopperGolemItemText.snapshotFromStatue(statue, RegistryAccess.EMPTY)
		);
	}

	@Test
	void plainVanillaNameRemainsPlainAndCreatesNoOwnedSnapshot() {
		Component plainName = Component.literal("Copper Friend");
		ItemStack statue = CopperGolemItemText.copyFromGolem(
				new FakeCarrier(plainName, new CompoundTag()),
				new ItemStack(Items.STONE)
		);

		assertEquals(plainName, statue.get(DataComponents.CUSTOM_NAME));
		assertNull(ModDataComponents.getRawNameMarkup(statue));
		assertTrue(
				CopperGolemItemText.snapshotFromStatue(statue, RegistryAccess.EMPTY).isEmpty()
		);
	}

	private record FakeCarrier(Component name, CompoundTag snapshot)
			implements EntityItemTextCarrier {
		@Override
		public Component betterLore$currentName() {
			return name;
		}

		@Override
		public HolderLookup.Provider betterLore$registryAccess() {
			return RegistryAccess.EMPTY;
		}

		@Override
		public CompoundTag betterLore$copyItemTextData() {
			return snapshot.copy();
		}

		@Override
		public void betterLore$setItemTextData(CompoundTag itemTextData) {
			throw new UnsupportedOperationException();
		}
	}
}
