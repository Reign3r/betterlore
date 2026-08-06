package com.reign.betterlore.net;

import com.reign.betterlore.compat.CompatibilityRuntime;
import com.reign.betterlore.compat.net.NetworkIdentifiersBackend;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Stable facade for Minecraft's versioned/remapped resource identifier API. */
public final class NetworkIdentifiers {
	private static final String BACKEND_CLASS_NAME =
			"com.reign.betterlore.compat.net.NetworkIdentifiersBackendImpl";

	private NetworkIdentifiers() {
	}

	public static Object create(String path) {
		return backend().create(path);
	}

	public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
		return backend().payloadType(path);
	}

	private static NetworkIdentifiersBackend backend() {
		return BackendHolder.INSTANCE;
	}

	private static final class BackendHolder {
		private static final NetworkIdentifiersBackend INSTANCE = CompatibilityRuntime.instantiate(
				BACKEND_CLASS_NAME,
				NetworkIdentifiersBackend.class
		);
	}
}
