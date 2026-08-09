package com.reign.betterlore.world.compat;

//? if >=1.21.5 {
import com.reign.betterlore.test.MinecraftTestBootstrap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
//? }

class PlacedItemTextStorageCodecTest {
	//? if >=1.21.5 {
	@BeforeAll
	static void requireModernStorageCodec() {
		try {
			PlacedItemTextStorageBackendImpl.class.getDeclaredField("CODEC");
		} catch (NoSuchFieldException ignored) {
			Assumptions.assumeTrue(false, "The dual-format SavedData codec begins in 1.21.5");
		}
		MinecraftTestBootstrap.ensureCodecRegistries();
	}

	@Test
	void pre1215CompoundRootAndComponentAliasMigrateLosslessly() throws Exception {
		Component foreign = Component.literal("Foreign")
				.withStyle(style -> style.withBold(true).withInsertion("another_mod:marker"));
		JsonObject entry = baseEntry();
		entry.add("foreign_lore_components", encodeComponents(List.of(foreign)));
		JsonArray entries = new JsonArray();
		entries.add(entry);
		JsonObject legacyRoot = new JsonObject();
		legacyRoot.add("entries", entries);

		JsonObject migrated = roundTripFirstEntry(legacyRoot);

		assertEquals(List.of(foreign), decodeComponents(migrated.get("foreign_lore")));
		assertFalse(migrated.has("foreign_lore_components"));
		assertEquals("Legacy foreign", migrated.get("lore").getAsString());
		assertEquals("<b>Owned</b>", migrated.get("owned_lore").getAsString());
	}

	@Test
	void modernListRootRoundTripsCanonicalForeignLore() throws Exception {
		Component foreign = Component.literal("Current")
				.withStyle(style -> style.withUnderlined(true));
		JsonObject entry = baseEntry();
		entry.add("foreign_lore", encodeComponents(List.of(foreign)));
		JsonArray currentRoot = new JsonArray();
		currentRoot.add(entry);

		JsonObject roundTripped = roundTripFirstEntry(currentRoot);

		assertEquals(List.of(foreign), decodeComponents(roundTripped.get("foreign_lore")));
		assertFalse(roundTripped.has("foreign_lore_components"));
	}

	@Test
	void interimQuickTextForeignFieldAlsoMigrates() throws Exception {
		JsonObject entry = baseEntry();
		entry.addProperty("foreign_lore", "<i>Interim</i>");
		JsonArray interimRoot = new JsonArray();
		interimRoot.add(entry);

		JsonObject migrated = roundTripFirstEntry(interimRoot);
		List<Component> foreign = decodeComponents(migrated.get("foreign_lore"));

		assertEquals(1, foreign.size());
		assertEquals("Interim", foreign.get(0).getString());
		assertTrue(foreign.get(0).getSiblings().get(0).getStyle().isItalic());
	}

	private static JsonObject baseEntry() {
		JsonObject entry = new JsonObject();
		entry.addProperty("pos", 42L);
		entry.addProperty("item", "minecraft:stone");
		entry.addProperty("lore", "Legacy foreign");
		entry.addProperty("owned_lore", "<b>Owned</b>");
		entry.addProperty("name", "<i>Name</i>");
		return entry;
	}

	private static JsonElement encodeComponents(List<Component> components) {
		return ComponentSerialization.CODEC.listOf()
				.encodeStart(JsonOps.INSTANCE, components)
				.getOrThrow();
	}

	private static List<Component> decodeComponents(JsonElement encoded) {
		return List.copyOf(ComponentSerialization.CODEC.listOf()
				.parse(JsonOps.INSTANCE, encoded)
				.getOrThrow());
	}

	private static JsonObject roundTripFirstEntry(JsonElement encoded) throws Exception {
		Codec<Object> codec = storageCodec();
		Object storage = codec.parse(JsonOps.INSTANCE, encoded).getOrThrow();
		JsonElement canonical = codec.encodeStart(JsonOps.INSTANCE, storage).getOrThrow();
		assertTrue(canonical.isJsonArray());
		assertEquals(1, canonical.getAsJsonArray().size());
		return canonical.getAsJsonArray().get(0).getAsJsonObject();
	}

	@SuppressWarnings("unchecked")
	private static Codec<Object> storageCodec() throws Exception {
		Field field = PlacedItemTextStorageBackendImpl.class.getDeclaredField("CODEC");
		field.setAccessible(true);
		return (Codec<Object>) field.get(null);
	}
	//? }
}
