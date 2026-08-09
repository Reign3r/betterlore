package com.reign.betterlore.world;

import com.reign.betterlore.AnvilLoreMod;
import com.reign.betterlore.world.compat.ComponentListNbtCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/** Preserves Better Lore text while vanilla replaces one bucket item with another. */
public final class BucketItemText {
	private static final String BUCKET_IDENTITY_KEY = "better_lore_bucket_identity";
	private static final String EXACT_IDENTITY_MARKER_KEY = "exact_visible_text";
	private static final String VISIBLE_NAME_COMPONENTS_KEY = "visible_name_components";
	private static final String VISIBLE_LORE_COMPONENTS_KEY = "visible_lore_components";

	private BucketItemText() {
	}

	/**
	 * Transfers the source bucket's identity into a newly-filled bucket.
	 * Mob buckets keep that identity private because their visible text belongs
	 * to the contained mob until it is released.
	 */
	public static void onFilledBucket(
			ItemStack sourceBucket,
			ItemStack filledBucket,
			HolderLookup.Provider registries
	) {
		if (sourceBucket.isEmpty() || filledBucket.isEmpty() || sourceBucket == filledBucket) {
			return;
		}
		// BucketItem.use obtains the empty result first, then passes that result
		// through ItemUtils.createFilledResult. The getEmptySuccessItem hook owns
		// that direction; treating the returned bucket as newly filled would copy
		// a mob bucket's visible mob text over its restored private bucket identity.
		if (filledBucket.is(Items.BUCKET)) {
			return;
		}

		if (filledBucket.getItem() instanceof MobBucketItem) {
			writeBucketIdentity(
					filledBucket,
					createBucketIdentity(sourceBucket, registries)
			);
			return;
		}

		copyDisplayedText(sourceBucket, filledBucket);
	}

	/** Removes container-only identity after a mob bucket's custom data is copied to its entity. */
	public static void removePrivateBucketIdentity(CompoundTag customData) {
		if (customData != null) {
			customData.remove(BUCKET_IDENTITY_KEY);
		}
	}

	/** Restores either a regular filled bucket's text or a mob bucket's hidden identity. */
	public static void onEmptiedBucket(
			ItemStack filledBucket,
			ItemStack emptyBucket,
			HolderLookup.Provider registries
	) {
		if (filledBucket.isEmpty() || emptyBucket.isEmpty() || filledBucket == emptyBucket) {
			return;
		}

		if (filledBucket.getItem() instanceof MobBucketItem) {
			restoreBucketIdentity(
					readBucketIdentity(filledBucket),
					emptyBucket,
					registries
			);
			return;
		}

		copyDisplayedText(filledBucket, emptyBucket);
	}

	/** Copies visible name/lore plus only Better Lore's private source metadata. */
	public static void copyDisplayedText(ItemStack source, ItemStack target) {
		Component name = source.get(DataComponents.CUSTOM_NAME);
		if (name == null) {
			target.remove(DataComponents.CUSTOM_NAME);
		} else {
			target.set(DataComponents.CUSTOM_NAME, name);
		}

		ItemLore lore = source.get(DataComponents.LORE);
		if (lore == null) {
			target.remove(DataComponents.LORE);
		} else {
			target.set(DataComponents.LORE, lore);
		}

		CompoundTag targetData = customData(target);
		targetData.remove(AnvilLoreMod.MOD_ID);
		CompoundTag sourceData = EntityItemText.onlyBetterLoreData(customData(source));
		Tag betterLore = sourceData.get(AnvilLoreMod.MOD_ID);
		if (betterLore != null) {
			targetData.put(AnvilLoreMod.MOD_ID, betterLore.copy());
		}
		setCustomData(target, targetData);
	}

	static CompoundTag readBucketIdentity(ItemStack stack) {
		CompoundTag root = customData(stack);
		//? if >=1.21.5 {
		return root.getCompoundOrEmpty(BUCKET_IDENTITY_KEY).copy();
		//? } else {
		return root.contains(BUCKET_IDENTITY_KEY, Tag.TAG_COMPOUND)
				? root.getCompound(BUCKET_IDENTITY_KEY).copy()
				: new CompoundTag();
		//? }
	}

	static void writeBucketIdentity(ItemStack stack, CompoundTag identity) {
		CompoundTag root = customData(stack);
		root.remove(BUCKET_IDENTITY_KEY);
		CompoundTag selected = EntityItemText.onlyItemTextSnapshotData(identity);
		copyPrivateField(identity, selected, EXACT_IDENTITY_MARKER_KEY);
		copyPrivateField(identity, selected, VISIBLE_NAME_COMPONENTS_KEY);
		copyPrivateField(identity, selected, VISIBLE_LORE_COMPONENTS_KEY);
		if (!selected.isEmpty()) {
			root.put(BUCKET_IDENTITY_KEY, selected);
		}
		setCustomData(stack, root);
	}

	private static CompoundTag createBucketIdentity(
			ItemStack source,
			HolderLookup.Provider registries
	) {
		CompoundTag identity = EntityItemText.itemTextDataFromStack(source, registries);
		identity.putBoolean(EXACT_IDENTITY_MARKER_KEY, true);

		Component name = source.get(DataComponents.CUSTOM_NAME);
		if (name != null) {
			identity.put(
					VISIBLE_NAME_COMPONENTS_KEY,
					ComponentListNbtCodec.encode(java.util.List.of(name), registries)
			);
		}

		ItemLore lore = source.get(DataComponents.LORE);
		if (lore != null && !lore.lines().isEmpty()) {
			identity.put(
					VISIBLE_LORE_COMPONENTS_KEY,
					ComponentListNbtCodec.encode(lore.lines(), registries)
			);
		}
		return identity;
	}

	private static void restoreBucketIdentity(
			CompoundTag identity,
			ItemStack target,
			HolderLookup.Provider registries
	) {
		if (!identity.contains(EXACT_IDENTITY_MARKER_KEY)) {
			// Compatibility with buckets created by the first 1.3.0 test build.
			EntityItemText.restoreStoredText(identity, target, registries);
			return;
		}

		java.util.List<Component> names = decodeComponents(
				identity,
				VISIBLE_NAME_COMPONENTS_KEY,
				registries
		);
		if (names.isEmpty()) {
			target.remove(DataComponents.CUSTOM_NAME);
		} else {
			target.set(DataComponents.CUSTOM_NAME, names.getFirst());
		}

		java.util.List<Component> lore = decodeComponents(
				identity,
				VISIBLE_LORE_COMPONENTS_KEY,
				registries
		);
		if (lore.isEmpty()) {
			target.remove(DataComponents.LORE);
		} else {
			target.set(DataComponents.LORE, new ItemLore(lore));
		}

		CompoundTag targetData = customData(target);
		targetData.remove(AnvilLoreMod.MOD_ID);
		CompoundTag selected = EntityItemText.onlyBetterLoreData(identity);
		Tag betterLore = selected.get(AnvilLoreMod.MOD_ID);
		if (betterLore != null) {
			targetData.put(AnvilLoreMod.MOD_ID, betterLore.copy());
		}
		setCustomData(target, targetData);
	}

	private static java.util.List<Component> decodeComponents(
			CompoundTag identity,
			String key,
			HolderLookup.Provider registries
	) {
		Tag encoded = identity.get(key);
		if (encoded == null) {
			return java.util.List.of();
		}
		try {
			return ComponentListNbtCodec.decode(encoded, registries);
		} catch (RuntimeException ignored) {
			return java.util.List.of();
		}
	}

	private static void copyPrivateField(CompoundTag source, CompoundTag target, String key) {
		Tag value = source.get(key);
		if (value != null) {
			target.put(key, value.copy());
		}
	}

	private static CompoundTag customData(ItemStack stack) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		return customData == null || customData.isEmpty()
				? new CompoundTag()
				: customData.copyTag();
	}

	private static void setCustomData(ItemStack stack, CompoundTag root) {
		if (root.isEmpty()) {
			stack.remove(DataComponents.CUSTOM_DATA);
		} else {
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
		}
	}
}
