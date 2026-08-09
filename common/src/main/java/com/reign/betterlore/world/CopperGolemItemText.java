package com.reign.betterlore.world;

import com.reign.betterlore.access.EntityItemTextCarrier;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Item-shaped bridge between copper golem entities and their statue blocks. */
public final class CopperGolemItemText {
	private CopperGolemItemText() {
	}

	/** Reconstructs the statue item text represented by a golem entity. */
	public static ItemStack copyFromGolem(
			EntityItemTextCarrier golem,
			ItemStack statueStack
	) {
		if (golem == null || statueStack.isEmpty()) {
			return statueStack;
		}

		Component name = golem.betterLore$currentName();
		if (name == null) {
			statueStack.remove(DataComponents.CUSTOM_NAME);
		} else {
			statueStack.set(DataComponents.CUSTOM_NAME, name);
		}
		return EntityItemText.restore(golem, statueStack);
	}

	/** Selects the exact Better Lore snapshot restored from a statue record. */
	public static CompoundTag snapshotFromStatue(
			ItemStack statueStack,
			HolderLookup.Provider registries
	) {
		return EntityItemText.itemTextDataFromStack(statueStack, registries);
	}
}
