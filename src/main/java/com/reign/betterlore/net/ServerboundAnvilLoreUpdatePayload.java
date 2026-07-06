package com.reign.betterlore.net;

import com.reign.betterlore.lore.LoreMarkupParser;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ServerboundAnvilLoreUpdatePayload(int containerId, int sessionId, String rawLoreMarkup) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ServerboundAnvilLoreUpdatePayload> TYPE =
			new CustomPacketPayload.Type<>(AnvilLoreNetworking.id("anvil_lore_update"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundAnvilLoreUpdatePayload> CODEC =
			CustomPacketPayload.codec(ServerboundAnvilLoreUpdatePayload::write, ServerboundAnvilLoreUpdatePayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeContainerId(containerId);
		buf.writeVarInt(sessionId);
		buf.writeUtf(rawLoreMarkup, LoreMarkupParser.MAX_RAW_CHARS);
	}

	private static ServerboundAnvilLoreUpdatePayload read(RegistryFriendlyByteBuf buf) {
		return new ServerboundAnvilLoreUpdatePayload(
				buf.readContainerId(),
				buf.readVarInt(),
				buf.readUtf(LoreMarkupParser.MAX_RAW_CHARS)
		);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
