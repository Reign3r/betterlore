package com.reign.itemlore.net;

import com.reign.itemlore.lore.LoreMarkupParser;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ServerboundAnvilNameUpdatePayload(int containerId, int sessionId, String rawNameMarkup) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ServerboundAnvilNameUpdatePayload> TYPE =
			new CustomPacketPayload.Type<>(AnvilLoreNetworking.id("anvil_name_update"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundAnvilNameUpdatePayload> CODEC =
			CustomPacketPayload.codec(ServerboundAnvilNameUpdatePayload::write, ServerboundAnvilNameUpdatePayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeContainerId(containerId);
		buf.writeVarInt(sessionId);
		buf.writeUtf(rawNameMarkup, LoreMarkupParser.MAX_RAW_CHARS);
	}

	private static ServerboundAnvilNameUpdatePayload read(RegistryFriendlyByteBuf buf) {
		return new ServerboundAnvilNameUpdatePayload(
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
