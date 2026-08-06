package com.reign.betterlore.world.compat;

import com.reign.betterlore.lore.LoreComponents;
import com.reign.betterlore.lore.LoreMarkupDecompiler;
import com.reign.betterlore.lore.LoreMarkupParser;
import com.reign.betterlore.lore.ParseResult;
import com.reign.betterlore.net.AnvilLoreNetworking;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
//? if >=1.21.5 {
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
//? } else {
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
//? }
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
//? if >=26.1 {
import net.minecraft.resources.Identifier;
//? }
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=1.21.5 {
import net.minecraft.world.level.saveddata.SavedDataType;
//? }

import java.util.ArrayList;
import java.util.List;

/** Minecraft-version-specific implementation selected by CompatibilityRuntime. */
public final class PlacedItemTextStorageBackendImpl implements PlacedItemTextStorageBackend {
	private static final String DATA_NAME = "better_lore_placed_item_text";
	private static final String ENTRIES_KEY = "entries";

	//? if >=1.21.5 {
	private static final Codec<Storage> CODEC = SerializedEntry.CODEC.listOf().xmap(
			Storage::new,
			Storage::serializedEntries
	);

	//? if >=26.1 {
	private static final SavedDataType<Storage> TYPE = new SavedDataType<>(
			(Identifier) AnvilLoreNetworking.id("placed_item_text"),
			Storage::new,
			CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE
	);
	//? } else {
	private static final SavedDataType<Storage> TYPE = new SavedDataType<>(
			DATA_NAME,
			Storage::new,
			CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE
	);
	//? }
	//? } else {
	private static final SavedData.Factory<Storage> FACTORY = new SavedData.Factory<>(
			Storage::new,
			Storage::load,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE
	);
	//? }

	public PlacedItemTextStorageBackendImpl() {
	}

	@Override
	public void remember(Level level, BlockPos pos, ItemStack placedStack) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}

		long packedPos = pos.asLong();
		String rawLore = LoreMarkupDecompiler.toSafeLoreMarkup(placedStack);
		String rawName = LoreMarkupDecompiler.toSafeNameMarkup(placedStack);
		if (rawLore.isEmpty() && rawName.isEmpty()) {
			Storage storage = getIfPresent(serverLevel);
			if (storage != null && storage.entries.remove(packedPos) != null) {
				storage.setDirty();
			}
			return;
		}

		Storage storage = get(serverLevel);
		String itemId = BuiltInRegistries.ITEM.getKey(placedStack.getItem()).toString();
		ItemText replacement = new ItemText(itemId, rawLore, rawName);
		if (!replacement.equals(storage.entries.put(packedPos, replacement))) {
			storage.setDirty();
		}
	}

	@Override
	public void move(ServerLevel level, long sourcePos, long destinationPos) {
		Storage storage = getIfPresent(level);
		if (storage != null && PlacedItemTextStorageBackend.moveEntry(
				storage.entries,
				sourcePos,
				destinationPos
		)) {
			storage.setDirty();
		}
	}

	@Override
	public void restoreDrop(Level level, BlockPos pos, ItemStack droppedStack) {
		if (!(level instanceof ServerLevel serverLevel) || droppedStack.isEmpty()) {
			return;
		}

		Storage storage = getIfPresent(serverLevel);
		if (storage == null) {
			return;
		}

		long packedPos = pos.asLong();
		ItemText entry = storage.entries.get(packedPos);
		if (entry == null) {
			return;
		}

		String droppedItemId = BuiltInRegistries.ITEM.getKey(droppedStack.getItem()).toString();
		if (!entry.itemId().equals(droppedItemId)) {
			return;
		}

		applyLore(droppedStack, entry.rawLore());
		applyName(droppedStack, entry.rawName());
		storage.entries.remove(packedPos);
		storage.setDirty();
	}

	private static void applyLore(ItemStack stack, String rawLore) {
		ParseResult parsed = LoreMarkupParser.parse(rawLore);
		if (parsed.isSuccess()) {
			LoreComponents.applyTo(stack, rawLore, parsed.document());
		}
	}

	private static void applyName(ItemStack stack, String rawName) {
		ParseResult parsed = LoreMarkupParser.parseName(rawName);
		if (parsed.isSuccess()) {
			LoreComponents.applyNameTo(stack, rawName, parsed.document());
		}
	}

	private static Storage get(ServerLevel level) {
		//? if >=1.21.5 {
		return level.getDataStorage().computeIfAbsent(TYPE);
		//? } else {
		return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
		//? }
	}

	private static Storage getIfPresent(ServerLevel level) {
		//? if >=1.21.5 {
		return level.getDataStorage().get(TYPE);
		//? } else {
		return level.getDataStorage().get(FACTORY, DATA_NAME);
		//? }
	}

	private static final class Storage extends SavedData {
		private final Long2ObjectOpenHashMap<ItemText> entries;

		private Storage() {
			this(2);
		}

		private Storage(int expectedEntries) {
			entries = new Long2ObjectOpenHashMap<>(Math.max(2, expectedEntries));
		}

		//? if >=1.21.5 {
		private Storage(List<SerializedEntry> serializedEntries) {
			this(serializedEntries.size());
			for (SerializedEntry entry : serializedEntries) {
				entries.put(entry.packedPos(), entry.itemText());
			}
		}

		private List<SerializedEntry> serializedEntries() {
			List<SerializedEntry> serializedEntries = new ArrayList<>(entries.size());
			for (Long2ObjectMap.Entry<ItemText> entry : entries.long2ObjectEntrySet()) {
				serializedEntries.add(new SerializedEntry(entry.getLongKey(), entry.getValue()));
			}
			return serializedEntries;
		}
		//? } else {
		private static Storage load(CompoundTag tag, HolderLookup.Provider registries) {
			ListTag serializedEntries = tag.getList(ENTRIES_KEY, Tag.TAG_COMPOUND);
			Storage storage = new Storage(serializedEntries.size());
			for (int index = 0; index < serializedEntries.size(); index++) {
				CompoundTag serialized = serializedEntries.getCompound(index);
				long packedPos = serialized.getLong("pos");
				ItemText itemText = new ItemText(
						serialized.getString("item"),
						serialized.getString("lore"),
						serialized.getString("name")
				);
				if (!itemText.itemId().isEmpty()) {
					storage.entries.put(packedPos, itemText);
				}
			}
			return storage;
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			ListTag serializedEntries = new ListTag();
			for (Long2ObjectMap.Entry<ItemText> entry : entries.long2ObjectEntrySet()) {
				ItemText itemText = entry.getValue();
				CompoundTag serialized = new CompoundTag();
				serialized.putLong("pos", entry.getLongKey());
				serialized.putString("item", itemText.itemId());
				if (!itemText.rawLore().isEmpty()) {
					serialized.putString("lore", itemText.rawLore());
				}
				if (!itemText.rawName().isEmpty()) {
					serialized.putString("name", itemText.rawName());
				}
				serializedEntries.add(serialized);
			}
			tag.put(ENTRIES_KEY, serializedEntries);
			return tag;
		}
		//? }
	}

	private record ItemText(String itemId, String rawLore, String rawName) {
	}

	private record SerializedEntry(long packedPos, ItemText itemText) {
		//? if >=1.21.5 {
		private static final Codec<SerializedEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.LONG.fieldOf("pos").forGetter(SerializedEntry::packedPos),
				Codec.STRING.fieldOf("item").forGetter(entry -> entry.itemText().itemId()),
				Codec.STRING.optionalFieldOf("lore", "").forGetter(entry -> entry.itemText().rawLore()),
				Codec.STRING.optionalFieldOf("name", "").forGetter(entry -> entry.itemText().rawName())
		).apply(instance, (packedPos, itemId, rawLore, rawName) ->
				new SerializedEntry(packedPos, new ItemText(itemId, rawLore, rawName))));
		//? }
	}
}
