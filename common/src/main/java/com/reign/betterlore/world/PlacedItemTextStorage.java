package com.reign.betterlore.world;

import com.reign.betterlore.lore.LoreComponents;
import com.reign.betterlore.lore.LoreMarkupDecompiler;
import com.reign.betterlore.lore.LoreMarkupParser;
import com.reign.betterlore.lore.ParseResult;
import com.reign.betterlore.net.AnvilLoreNetworking;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=1.21.5 {
import net.minecraft.world.level.saveddata.SavedDataType;
//? }

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists the editable name/lore source for blocks placed from decorated items.
 *
 * <p>Most blocks do not have a block entity, so vanilla has nowhere to retain
 * item components between placement and a later block drop. Keeping only the
 * safe markup and original item id makes the behavior work for ordinary blocks
 * (including anvils) without replacing blocks or introducing block entities.</p>
 */
public final class PlacedItemTextStorage extends SavedData {
	private static final String DATA_NAME = "better_lore_placed_item_text";
	private static final String ENTRIES_KEY = "entries";
	private final Map<Long, Entry> entries = new HashMap<>();

	//? if >=1.21.5 {
	private static final Codec<PlacedItemTextStorage> CODEC = Entry.CODEC.listOf().xmap(
			PlacedItemTextStorage::new,
			PlacedItemTextStorage::serializedEntries
	);

	//? if >=26.1 {
	private static final SavedDataType<PlacedItemTextStorage> TYPE = new SavedDataType<>(
			AnvilLoreNetworking.id("placed_item_text"),
			PlacedItemTextStorage::new,
			CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE
	);
	//? } else {
	private static final SavedDataType<PlacedItemTextStorage> TYPE = new SavedDataType<>(
			DATA_NAME,
			PlacedItemTextStorage::new,
			CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE
	);
	//? }
	//? } else {
	private static final SavedData.Factory<PlacedItemTextStorage> FACTORY = new SavedData.Factory<>(
			PlacedItemTextStorage::new,
			PlacedItemTextStorage::load,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE
	);
	//? }

	public PlacedItemTextStorage() {
	}

	//? if >=1.21.5 {
	private PlacedItemTextStorage(List<Entry> serializedEntries) {
		for (Entry entry : serializedEntries) {
			entries.put(entry.packedPos(), entry);
		}
	}

	private List<Entry> serializedEntries() {
		return List.copyOf(entries.values());
	}
	//? }

	public static void remember(Level level, BlockPos pos, ItemStack placedStack) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}

		PlacedItemTextStorage storage = get(serverLevel);
		long packedPos = pos.asLong();
		String rawLore = LoreMarkupDecompiler.toSafeLoreMarkup(placedStack);
		String rawName = LoreMarkupDecompiler.toSafeNameMarkup(placedStack);
		if (rawLore.isEmpty() && rawName.isEmpty()) {
			if (storage.entries.remove(packedPos) != null) {
				storage.setDirty();
			}
			return;
		}

		String itemId = BuiltInRegistries.ITEM.getKey(placedStack.getItem()).toString();
		Entry replacement = new Entry(packedPos, itemId, rawLore, rawName);
		if (!replacement.equals(storage.entries.put(packedPos, replacement))) {
			storage.setDirty();
		}
	}

	/** Applies and consumes saved text when the matching placed block item drops. */
	public static void restoreDrop(Level level, BlockPos pos, ItemStack droppedStack) {
		if (!(level instanceof ServerLevel serverLevel) || droppedStack.isEmpty()) {
			return;
		}

		PlacedItemTextStorage storage = get(serverLevel);
		long packedPos = pos.asLong();
		Entry entry = storage.entries.get(packedPos);
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

	private static PlacedItemTextStorage get(ServerLevel level) {
		//? if >=1.21.5 {
		return level.getDataStorage().computeIfAbsent(TYPE);
		//? } else {
		return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
		//? }
	}

	//? if <1.21.5 {
	private static PlacedItemTextStorage load(CompoundTag tag, HolderLookup.Provider registries) {
		PlacedItemTextStorage storage = new PlacedItemTextStorage();
		ListTag serializedEntries = tag.getList(ENTRIES_KEY, Tag.TAG_COMPOUND);
		for (int index = 0; index < serializedEntries.size(); index++) {
			CompoundTag serialized = serializedEntries.getCompound(index);
			Entry entry = new Entry(
					serialized.getLong("pos"),
					serialized.getString("item"),
					serialized.getString("lore"),
					serialized.getString("name")
			);
			if (!entry.itemId().isEmpty()) {
				storage.entries.put(entry.packedPos(), entry);
			}
		}
		return storage;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag serializedEntries = new ListTag();
		for (Entry entry : entries.values()) {
			CompoundTag serialized = new CompoundTag();
			serialized.putLong("pos", entry.packedPos());
			serialized.putString("item", entry.itemId());
			serialized.putString("lore", entry.rawLore());
			serialized.putString("name", entry.rawName());
			serializedEntries.add(serialized);
		}
		tag.put(ENTRIES_KEY, serializedEntries);
		return tag;
	}
	//? }

	private record Entry(long packedPos, String itemId, String rawLore, String rawName) {
		//? if >=1.21.5 {
		private static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.LONG.fieldOf("pos").forGetter(Entry::packedPos),
				Codec.STRING.fieldOf("item").forGetter(Entry::itemId),
				Codec.STRING.optionalFieldOf("lore", "").forGetter(Entry::rawLore),
				Codec.STRING.optionalFieldOf("name", "").forGetter(Entry::rawName)
		).apply(instance, Entry::new));
		//? }
	}
}
