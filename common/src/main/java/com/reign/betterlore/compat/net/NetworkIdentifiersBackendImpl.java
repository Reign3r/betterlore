package com.reign.betterlore.compat.net;

import com.reign.betterlore.AnvilLoreMod;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//? } else {
import net.minecraft.resources.ResourceLocation;
//? }

/** Minecraft-version-specific identifier implementation selected at runtime. */
public final class NetworkIdentifiersBackendImpl implements NetworkIdentifiersBackend {
	public NetworkIdentifiersBackendImpl() {
	}

	@Override
	public Object create(String path) {
		//? if >=1.21.11 {
		return Identifier.fromNamespaceAndPath(AnvilLoreMod.MOD_ID, path);
		//? } else if >=1.21 {
		return ResourceLocation.fromNamespaceAndPath(AnvilLoreMod.MOD_ID, path);
		//? } else {
		return new ResourceLocation(AnvilLoreMod.MOD_ID, path);
		//? }
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
		// The concrete cast stays in version-processed code so Fabric Loom can
		// remap the identifier descriptor as well as the class reference.
		//? if >=1.21.11 {
		return new CustomPacketPayload.Type<>((Identifier) create(path));
		//? } else {
		return new CustomPacketPayload.Type<>((ResourceLocation) create(path));
		//? }
	}
}
