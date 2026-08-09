package com.reign.betterlore.mixin;

import com.reign.betterlore.access.EntityItemTextCarrier;
import com.reign.betterlore.world.EntityItemText;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Restores exact Better Lore source data to a broken armor stand item. */
@Mixin(ArmorStand.class)
public abstract class ArmorStandItemTextMixin {
	@ModifyArg(
			method = "brokenByPlayer",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/block/Block;popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V"
			),
			index = 2
	)
	private ItemStack betterLore$restoreArmorStandItem(ItemStack returnedStack) {
		return EntityItemText.restore((EntityItemTextCarrier) (Object) this, returnedStack);
	}
}
