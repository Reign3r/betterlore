package com.reign.betterlore.mixin;

import com.reign.betterlore.access.EntityItemTextCarrier;
import com.reign.betterlore.world.EntityItemText;
//? if >=26.2 {
import net.minecraft.world.entity.Bucketable;
//? } else {
import net.minecraft.world.entity.animal.Bucketable;
//? }
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restores exact Better Lore source data when a placed fish returns to its bucket. */
@Mixin(Bucketable.class)
public interface BucketableItemTextMixin {
	@Inject(method = "saveDefaultDataToBucketTag", at = @At("RETURN"))
	private static void betterLore$restoreBucketItemText(Mob entity, ItemStack bucket, CallbackInfo ci) {
		EntityItemText.restore((EntityItemTextCarrier) (Object) entity, bucket);
	}
}
