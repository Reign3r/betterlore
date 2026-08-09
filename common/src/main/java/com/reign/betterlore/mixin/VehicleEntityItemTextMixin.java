package com.reign.betterlore.mixin;

import com.reign.betterlore.access.EntityItemTextCarrier;
import com.reign.betterlore.world.EntityItemText;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Restores exact Better Lore source data to broken boats and minecarts. */
@Mixin(VehicleEntity.class)
public abstract class VehicleEntityItemTextMixin {
	//? if >=1.21.2 {
	@ModifyArg(
			method = "destroy(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/Item;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/vehicle/VehicleEntity;spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;"
			),
			index = 1
	)
	//? } else {
	@ModifyArg(
			method = "destroy(Lnet/minecraft/world/item/Item;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/vehicle/VehicleEntity;spawnAtLocation(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;"
			),
			index = 0
	)
	//? }
	private ItemStack betterLore$restoreVehicleItem(ItemStack returnedStack) {
		return EntityItemText.restore((EntityItemTextCarrier) (Object) this, returnedStack);
	}
}
