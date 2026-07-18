package com.reign.betterlore.net;

import com.reign.betterlore.lore.LoreMarkupParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClientboundAnvilLoreStatePayload(int containerId, int sessionId, String safeExistingLoreMarkup, String safeExistingNameMarkup, int loreEditLevelCost) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ClientboundAnvilLoreStatePayload> TYPE =
			new CustomPacketPayload.Type<>(AnvilLoreNetworking.id("anvil_lore_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundAnvilLoreStatePayload> CODEC =
			CustomPacketPayload.codec(ClientboundAnvilLoreStatePayload::write, ClientboundAnvilLoreStatePayload::read);

	public void write(FriendlyByteBuf buf) {
		//? if >=1.21.5 {
		buf.writeContainerId(containerId);
		//? } else {
		buf.writeVarInt(containerId);
		//? }
		buf.writeVarInt(sessionId);
		buf.writeUtf(safeExistingLoreMarkup, LoreMarkupParser.MAX_RAW_CHARS);
		buf.writeUtf(safeExistingNameMarkup, LoreMarkupParser.MAX_RAW_CHARS);
		buf.writeVarInt(loreEditLevelCost);
	}

	public static ClientboundAnvilLoreStatePayload read(FriendlyByteBuf buf) {
		return new ClientboundAnvilLoreStatePayload(
				//? if >=1.21.5 {
				buf.readContainerId(),
				//? } else {
				buf.readVarInt(),
				//? }
				buf.readVarInt(),
				buf.readUtf(LoreMarkupParser.MAX_RAW_CHARS),
				buf.readUtf(LoreMarkupParser.MAX_RAW_CHARS),
				buf.readVarInt()
		);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
