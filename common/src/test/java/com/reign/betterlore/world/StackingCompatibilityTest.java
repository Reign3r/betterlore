package com.reign.betterlore.world;

import com.reign.betterlore.ModDataComponents;
import com.reign.betterlore.test.MinecraftTestBootstrap;
import com.reign.betterlore.lore.LoreOwnership;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StackingCompatibilityTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureCodecRegistries();
	}

	@Test
	void privateBetterLoreSourceDoesNotSplitVisiblyIdenticalStacks() {
		ItemStack recovered = namedStone("<gr #ff0000 #0000ff>Stone</gr>");
		ItemStack original = namedStone("<c #7f00ff>Stone</c>");

		assertFalse(ItemStack.isSameItemSameComponents(recovered, original));
		assertTrue(StackingCompatibility.matchesIgnoringBetterLore(recovered, original));

		ItemStack untouched = new ItemStack(Items.STONE);
		untouched.set(DataComponents.CUSTOM_NAME, Component.literal("Stone"));
		assertTrue(StackingCompatibility.matchesIgnoringBetterLore(recovered, untouched));
	}

	@Test
	void unrelatedCustomDataStillPreventsStacking() {
		ItemStack recovered = namedStone("<c #ff0000>Stone</c>");
		ItemStack different = namedStone("<c #ff0000>Stone</c>");
		CompoundTag customData = new CompoundTag();
		customData.putString("other_mod_data", "different");
		different.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));

		assertFalse(StackingCompatibility.matchesIgnoringBetterLore(recovered, different));
	}

	@Test
	void visibleComponentsStillPreventStacking() {
		ItemStack recovered = namedStone("Stone", "<c #ff0000>Stone</c>");
		ItemStack differentName = namedStone("Different", "<c #ff0000>Different</c>");

		assertFalse(StackingCompatibility.matchesIgnoringBetterLore(recovered, differentName));
	}

	@Test
	void currentSourceAndOwnershipSurviveLegacyMerge() {
		ItemStack legacy = namedStone("<gr #ff0000 #0000ff>Stone</gr>");
		legacy.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Stone"))));

		ItemStack current = namedStone("<gr type:oklab #ff0000 #0000ff>Stone</gr>");
		CompoundTag currentData = new CompoundTag();
		CompoundTag currentBetterLore = new CompoundTag();
		currentBetterLore.putString("raw_name_markup", "<gr type:oklab #ff0000 #0000ff>Stone</gr>");
		currentBetterLore.putInt("owned_name_version", 1);
		currentData.put("better_lore", currentBetterLore);
		current.set(DataComponents.CUSTOM_DATA, CustomData.of(currentData));
		current.set(DataComponents.LORE, new ItemLore(LoreOwnership.compose(
				List.of(), List.of(), List.of(Component.literal("Stone")))));

		assertTrue(StackingCompatibility.matchesIgnoringBetterLore(legacy, current));
		assertTrue(ModDataComponents.hasCurrentNameOwnership(legacy));
		assertEquals(
				ModDataComponents.getRawNameMarkup(current),
				ModDataComponents.getRawNameMarkup(legacy)
		);
		assertTrue(LoreOwnership.hasInlineOwnership(legacy.get(DataComponents.LORE).lines()));
	}

	private static ItemStack namedStone(String rawMarkup) {
		return namedStone("Stone", rawMarkup);
	}

	private static ItemStack namedStone(String visibleName, String rawMarkup) {
		ItemStack stack = new ItemStack(Items.STONE);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(visibleName));
		CompoundTag customData = new CompoundTag();
		CompoundTag betterLore = new CompoundTag();
		betterLore.putString("raw_name_markup", rawMarkup);
		customData.put("better_lore", betterLore);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
		return stack;
	}
}
