package com.reign.betterlore.compat.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModDataComponentsTagOpsTest {
	private static final String ROOT = "better_lore";

	@Test
	void ownedLoreMetadataPreservesUnrelatedCustomDataAndNameSource() {
		CompoundTag original = ModDataComponentsTagOps.setString(
				new CompoundTag(),
				"another_mod",
				"value",
				"preserved"
		);

		CompoundTag data = ModDataComponentsTagOps.setString(
				original,
				ROOT,
				"raw_name_markup",
				"<b>Name</b>"
		);
		data = ModDataComponentsTagOps.setString(
				data,
				ROOT,
				"raw_lore_markup",
				"<i>Owned</i>"
		);
		data = ModDataComponentsTagOps.setInt(data, ROOT, "owned_lore_version", 1);

		assertEquals("preserved", ModDataComponentsTagOps.getString(
				data,
				"another_mod",
				"value"
		));
		assertEquals("<i>Owned</i>", ModDataComponentsTagOps.getString(
				data,
				ROOT,
				"raw_lore_markup"
		));
		assertEquals(1, ModDataComponentsTagOps.getInt(data, ROOT, "owned_lore_version", 0));
		assertEquals("<b>Name</b>", ModDataComponentsTagOps.getString(
				data,
				ROOT,
				"raw_name_markup"
		));

		data = ModDataComponentsTagOps.remove(data, ROOT, "raw_lore_markup");
		data = ModDataComponentsTagOps.remove(data, ROOT, "owned_lore_version");

		assertNull(ModDataComponentsTagOps.getString(data, ROOT, "raw_lore_markup"));
		assertEquals(0, ModDataComponentsTagOps.getInt(data, ROOT, "owned_lore_version", 0));
		assertEquals("<b>Name</b>", ModDataComponentsTagOps.getString(
				data,
				ROOT,
				"raw_name_markup"
		));
		assertEquals("preserved", ModDataComponentsTagOps.getString(
				data,
				"another_mod",
				"value"
		));
		assertNull(ModDataComponentsTagOps.getString(original, ROOT, "raw_name_markup"));
	}
}
