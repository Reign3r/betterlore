package com.reign.betterlore.world.compat;

import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;

import java.util.List;

/** Registry-aware NBT serialization shared by persisted foreign-lore snapshots. */
public final class ComponentListNbtCodec {
	private static final Codec<List<Component>> CODEC = ComponentSerialization.CODEC.listOf();

	private ComponentListNbtCodec() {
	}

	public static Tag encode(List<Component> components, HolderLookup.Provider registries) {
		return CODEC.encodeStart(
				RegistryOps.create(NbtOps.INSTANCE, registries),
				List.copyOf(components)
		).getOrThrow();
	}

	public static List<Component> decode(Tag encoded, HolderLookup.Provider registries) {
		return List.copyOf(CODEC.parse(
				RegistryOps.create(NbtOps.INSTANCE, registries),
				encoded
		).getOrThrow());
	}
}
