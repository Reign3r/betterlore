package com.reign.betterlore.compat.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Version-neutral contract for Minecraft resource and payload identifiers. */
public interface NetworkIdentifiersBackend {
	Object create(String path);

	<T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path);
}
