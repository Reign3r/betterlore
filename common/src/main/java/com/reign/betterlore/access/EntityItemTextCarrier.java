package com.reign.betterlore.access;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Internal bridge exposing only Better Lore's item text payload retained by an entity. */
public interface EntityItemTextCarrier {
	Component betterLore$currentName();

	HolderLookup.Provider betterLore$registryAccess();

	CompoundTag betterLore$copyItemTextData();

	void betterLore$setItemTextData(CompoundTag itemTextData);
}
