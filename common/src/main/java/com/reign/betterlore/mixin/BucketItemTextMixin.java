package com.reign.betterlore.mixin;

import com.reign.betterlore.world.BucketItemText;
import com.reign.betterlore.world.EntityItemText;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Carries regular bucket text, or a mob bucket's hidden identity, to the empty bucket. */
@Mixin(BucketItem.class)
public abstract class BucketItemTextMixin {
	@Inject(method = "getEmptySuccessItem", at = @At("RETURN"))
	private static void betterLore$transferEmptyBucketText(
			ItemStack filled,
			Player player,
			CallbackInfoReturnable<ItemStack> cir
	) {
		BucketItemText.onEmptiedBucket(
				filled,
				cir.getReturnValue(),
				EntityItemText.registryAccess(player)
		);
	}
}
