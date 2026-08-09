package com.reign.betterlore.compat.data;

import net.minecraft.nbt.CompoundTag;
//? if <1.21.5 {
import net.minecraft.nbt.Tag;
//? }

/** Pure nested-tag operations used by the ItemStack custom-data adapter. */
final class ModDataComponentsTagOps {
	private ModDataComponentsTagOps() {
	}

	static String getString(CompoundTag root, String rootKey, String key) {
		String value = nestedString(nestedData(root, rootKey), key);
		return value.isEmpty() ? null : value;
	}

	static int getInt(CompoundTag root, String rootKey, String key, int fallback) {
		CompoundTag data = nestedData(root, rootKey);
		//? if >=1.21.5 {
		return data.getInt(key).orElse(fallback);
		//? } else {
		return data.contains(key, Tag.TAG_INT) ? data.getInt(key) : fallback;
		//? }
	}

	static CompoundTag setString(CompoundTag original, String rootKey, String key, String value) {
		CompoundTag root = original.copy();
		CompoundTag data = nestedData(root, rootKey).copy();
		data.putString(key, value);
		root.put(rootKey, data);
		return root;
	}

	static CompoundTag setInt(CompoundTag original, String rootKey, String key, int value) {
		CompoundTag root = original.copy();
		CompoundTag data = nestedData(root, rootKey).copy();
		data.putInt(key, value);
		root.put(rootKey, data);
		return root;
	}

	static CompoundTag remove(CompoundTag original, String rootKey, String key) {
		CompoundTag root = original.copy();
		CompoundTag data = nestedData(root, rootKey).copy();
		if (data.isEmpty()) {
			return root;
		}

		data.remove(key);
		if (data.isEmpty()) {
			root.remove(rootKey);
		} else {
			root.put(rootKey, data);
		}
		return root;
	}

	private static CompoundTag nestedData(CompoundTag root, String rootKey) {
		//? if >=1.21.5 {
		return root.getCompoundOrEmpty(rootKey);
		//? } else {
		return root.contains(rootKey, Tag.TAG_COMPOUND) ? root.getCompound(rootKey) : new CompoundTag();
		//? }
	}

	private static String nestedString(CompoundTag data, String key) {
		//? if >=1.21.5 {
		return data.getStringOr(key, "");
		//? } else {
		return data.contains(key, Tag.TAG_STRING) ? data.getString(key) : "";
		//? }
	}
}
