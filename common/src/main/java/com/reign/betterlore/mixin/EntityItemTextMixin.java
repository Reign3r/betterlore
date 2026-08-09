package com.reign.betterlore.mixin;

import com.reign.betterlore.access.EntityItemTextCarrier;
import com.reign.betterlore.world.BucketItemText;
import com.reign.betterlore.world.EntityItemText;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <1.21.5 {
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? }

/** Retains item custom data on versions where vanilla entities did not yet do so themselves. */
@Mixin(Entity.class)
public abstract class EntityItemTextMixin implements EntityItemTextCarrier {
	@Shadow
	public abstract Component getCustomName();

	@Shadow
	public abstract RegistryAccess registryAccess();

	//? if <1.21.5 {
	@Unique
	private CompoundTag betterLore$itemTextData = new CompoundTag();

	@Inject(method = "load", at = @At("HEAD"))
	private void betterLore$captureItemTextData(CompoundTag input, CallbackInfo ci) {
		betterLore$itemTextData = EntityItemText.readEntitySnapshot(input);
	}

	@Inject(method = "saveWithoutId", at = @At("TAIL"))
	private void betterLore$saveItemTextData(
			CompoundTag output,
			CallbackInfoReturnable<CompoundTag> cir
	) {
		EntityItemText.writeEntitySnapshot(betterLore$itemTextData, cir.getReturnValue());
	}
	//? }

	//? if >=1.21.5 {
	@Shadow
	public abstract <T> T get(DataComponentType<? extends T> componentType);

	@Shadow
	public abstract <T> void setComponent(DataComponentType<T> componentType, T value);

	@Inject(method = "applyComponentsFromItemStack", at = @At("TAIL"))
	private void betterLore$captureItemTextData(ItemStack source, CallbackInfo ci) {
		CustomData customData = get(DataComponents.CUSTOM_DATA);
		CompoundTag entityData = customData == null || customData.isEmpty()
				? new CompoundTag()
				: customData.copyTag();
		BucketItemText.removePrivateBucketIdentity(entityData);
		EntityItemText.writeEntitySnapshot(
				EntityItemText.itemTextDataFromStack(source, registryAccess()),
				entityData
		);
		setComponent(
				DataComponents.CUSTOM_DATA,
				entityData.isEmpty() ? CustomData.EMPTY : CustomData.of(entityData)
		);
	}
	//? }

	@Override
	public Component betterLore$currentName() {
		return getCustomName();
	}

	@Override
	public RegistryAccess betterLore$registryAccess() {
		return registryAccess();
	}

	@Override
	public CompoundTag betterLore$copyItemTextData() {
		//? if >=1.21.5 {
		CustomData customData = get(DataComponents.CUSTOM_DATA);
		return customData == null || customData.isEmpty()
				? new CompoundTag()
				: EntityItemText.readEntitySnapshot(customData.copyTag());
		//? } else {
		return betterLore$itemTextData.copy();
		//? }
	}

	@Override
	public void betterLore$setItemTextData(CompoundTag itemTextData) {
		//? if >=1.21.5 {
		CustomData customData = get(DataComponents.CUSTOM_DATA);
		CompoundTag entityData = customData == null || customData.isEmpty()
				? new CompoundTag()
				: customData.copyTag();
		EntityItemText.writeEntitySnapshot(itemTextData, entityData);
		setComponent(
				DataComponents.CUSTOM_DATA,
				entityData.isEmpty() ? CustomData.EMPTY : CustomData.of(entityData)
		);
		//? } else {
		betterLore$itemTextData = itemTextData == null
				? new CompoundTag()
				: EntityItemText.onlyItemTextSnapshotData(itemTextData);
		//? }
	}
}
