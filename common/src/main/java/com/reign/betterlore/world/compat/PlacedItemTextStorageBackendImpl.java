package com.reign.betterlore.world.compat;

import com.reign.betterlore.lore.LoreComponents;
import com.reign.betterlore.lore.LoreMarkupDecompiler;
import com.reign.betterlore.lore.LoreMarkupParser;
import com.reign.betterlore.lore.ParseResult;
import com.reign.betterlore.net.AnvilLoreNetworking;
import com.reign.betterlore.world.PlacedItemTextStorage;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
//? if >=1.21.5 {
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
//? } else {
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
//? }
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
//? if >=26.1 {
import net.minecraft.resources.Identifier;
//? }
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
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
	private static final String FOREIGN_LORE_COMPONENTS_KEY = "foreign_lore_components";

	//? if >=1.21.5 {
	private static final Codec<Either<List<Component>, String>> COMPATIBLE_FOREIGN_LORE_CODEC =
			Codec.either(ComponentSerialization.CODEC.listOf(), Codec.STRING);
	private static final Codec<List<SerializedEntry>> ENTRIES_CODEC = SerializedEntry.CODEC.listOf();
	private static final Codec<Storage> CODEC = Codec.either(
			ENTRIES_CODEC,
			ENTRIES_CODEC.fieldOf(ENTRIES_KEY).codec()
	).xmap(
			serialized -> new Storage(serialized.map(left -> left, right -> right)),
			storage -> Either.left(storage.serializedEntries())
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
		String ownedLore = LoreMarkupDecompiler.toSafeOwnedLoreMarkup(placedStack);
		List<Component> foreignLore = LoreComponents.foreignComponents(placedStack);
		String rawName = LoreMarkupDecompiler.toSafeNameMarkup(placedStack);
		if (ownedLore.isEmpty() && foreignLore.isEmpty() && rawName.isEmpty()) {
			Storage storage = getIfPresent(serverLevel);
			if (storage != null && storage.entries.remove(packedPos) != null) {
				storage.setDirty();
			}
			return;
		}

		Storage storage = get(serverLevel);
		String itemId = BuiltInRegistries.ITEM.getKey(placedStack.getItem()).toString();
		ItemText replacement = new ItemText(itemId, "", ownedLore, foreignLore, rawName);
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
		if (!PlacedItemTextStorage.matchesStoredItem(entry.itemId(), droppedItemId)) {
			return;
		}

		ParseResult parsedName = LoreMarkupParser.parseName(entry.rawName());
		String ownedLore = PlacedItemTextStorage.selectOwnedLore(
				LoreMarkupDecompiler.toSafeOwnedLoreMarkup(droppedStack),
				entry.ownedLore()
		);
		PlacedItemTextStorage.LoreRestorePlan lorePlan = PlacedItemTextStorage.planLoreRestore(
				LoreComponents.foreignComponents(droppedStack),
				entry.foreignLore(),
				entry.legacyLore(),
				ownedLore
		);
		if (!parsedName.isSuccess() || lorePlan == null) {
			return;
		}

		setForeignLore(droppedStack, lorePlan.foreignLore());
		applyOwnedLore(droppedStack, lorePlan.rawOwnedLore(), lorePlan.ownedDocument());
		LoreComponents.applyNameTo(droppedStack, entry.rawName(), parsedName.document());
		storage.entries.remove(packedPos);
		storage.setDirty();
	}

	private static void setForeignLore(ItemStack stack, List<Component> lines) {
		if (lines.isEmpty()) {
			stack.remove(DataComponents.LORE);
		} else {
			stack.set(DataComponents.LORE, new ItemLore(lines));
		}
	}

	private static void applyOwnedLore(
			ItemStack stack,
			String rawLore,
			com.reign.betterlore.lore.LoreDocument document
	) {
		LoreComponents.applyTo(stack, rawLore, document);
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
						serialized.getString("owned_lore"),
						parseStoredComponents(serialized, registries),
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
				if (!itemText.legacyLore().isEmpty()) {
					serialized.putString("lore", itemText.legacyLore());
				}
				if (!itemText.ownedLore().isEmpty()) {
					serialized.putString("owned_lore", itemText.ownedLore());
				}
				if (!itemText.foreignLore().isEmpty()) {
					serialized.put(
							FOREIGN_LORE_COMPONENTS_KEY,
							ComponentListNbtCodec.encode(itemText.foreignLore(), registries)
					);
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

	//? if <1.21.5 {
	private static List<Component> parseStoredComponents(
			CompoundTag serialized,
			HolderLookup.Provider registries
	) {
		Tag encoded = serialized.get(FOREIGN_LORE_COMPONENTS_KEY);
		if (encoded != null) {
			return ComponentListNbtCodec.decode(encoded, registries);
		}
		return parseStoredComponents(serialized.getString("foreign_lore"));
	}
	//? }

	private static List<Component> parseStoredComponents(String rawLore) {
		ParseResult parsed = LoreMarkupParser.parse(rawLore);
		return parsed.isSuccess() ? LoreComponents.toComponents(parsed.document()) : List.of();
	}

	//? if >=1.21.5 {
	private static List<Component> parseCompatibleForeignLore(
			Either<List<Component>, String> serialized
	) {
		return serialized.map(List::copyOf, PlacedItemTextStorageBackendImpl::parseStoredComponents);
	}
	//? }

	private record ItemText(
			String itemId,
			String legacyLore,
			String ownedLore,
			List<Component> foreignLore,
			String rawName
	) {
		private ItemText {
			foreignLore = List.copyOf(foreignLore);
		}
	}

	private record SerializedEntry(long packedPos, ItemText itemText) {
		//? if >=1.21.5 {
		private static final Codec<SerializedEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.LONG.fieldOf("pos").forGetter(SerializedEntry::packedPos),
				Codec.STRING.fieldOf("item").forGetter(entry -> entry.itemText().itemId()),
				Codec.STRING.optionalFieldOf("lore", "").forGetter(entry -> entry.itemText().legacyLore()),
				Codec.STRING.optionalFieldOf("owned_lore", "").forGetter(entry -> entry.itemText().ownedLore()),
				COMPATIBLE_FOREIGN_LORE_CODEC.optionalFieldOf(
						"foreign_lore",
						Either.left(List.of())
				).forGetter(entry -> Either.left(entry.itemText().foreignLore())),
				ComponentSerialization.CODEC.listOf().optionalFieldOf(
						FOREIGN_LORE_COMPONENTS_KEY,
						List.of()
				).forGetter(entry -> List.of()),
				Codec.STRING.optionalFieldOf("name", "").forGetter(entry -> entry.itemText().rawName())
		).apply(instance, (packedPos, itemId, legacyLore, ownedLore, foreignLore, foreignLoreAlias, rawName) ->
				new SerializedEntry(
						packedPos,
						new ItemText(
								itemId,
								legacyLore,
								ownedLore,
								PlacedItemTextStorage.mergeForeignLines(
										parseCompatibleForeignLore(foreignLore),
										foreignLoreAlias
								),
								rawName
						)
				)));
		//? }
	}
}
