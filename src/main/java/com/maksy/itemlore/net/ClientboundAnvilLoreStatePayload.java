package com.maksy.itemlore.net;

import com.maksy.itemlore.lore.LoreMarkupParser;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClientboundAnvilLoreStatePayload(int containerId, int sessionId, String safeExistingLoreMarkup, String safeExistingNameMarkup) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ClientboundAnvilLoreStatePayload> TYPE =
			new CustomPacketPayload.Type<>(AnvilLoreNetworking.id("anvil_lore_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundAnvilLoreStatePayload> CODEC =
			CustomPacketPayload.codec(ClientboundAnvilLoreStatePayload::write, ClientboundAnvilLoreStatePayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeContainerId(containerId);
		buf.writeVarInt(sessionId);
		buf.writeUtf(safeExistingLoreMarkup, LoreMarkupParser.MAX_RAW_CHARS);
		buf.writeUtf(safeExistingNameMarkup, LoreMarkupParser.MAX_RAW_CHARS);
	}

	private static ClientboundAnvilLoreStatePayload read(RegistryFriendlyByteBuf buf) {
		return new ClientboundAnvilLoreStatePayload(
				buf.readContainerId(),
				buf.readVarInt(),
				buf.readUtf(LoreMarkupParser.MAX_RAW_CHARS),
				buf.readUtf(LoreMarkupParser.MAX_RAW_CHARS)
		);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
