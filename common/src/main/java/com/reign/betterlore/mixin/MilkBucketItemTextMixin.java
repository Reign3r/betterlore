package com.reign.betterlore.mixin;

import com.reign.betterlore.world.BucketItemText;
import com.reign.betterlore.world.EntityItemText;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserves a milk bucket's text when consumption returns an empty bucket. */
@Mixin(ItemStack.class)
public abstract class MilkBucketItemTextMixin {
	@Unique
	private static final ThreadLocal<ItemStack> betterLore$milkBucket = new ThreadLocal<>();

	@Inject(method = "finishUsingItem", at = @At("HEAD"))
	private void betterLore$captureMilkBucket(
			net.minecraft.world.level.Level level,
			LivingEntity consumer,
			CallbackInfoReturnable<ItemStack> cir
	) {
		ItemStack source = (ItemStack) (Object) this;
		if (source.is(Items.MILK_BUCKET)) {
			betterLore$milkBucket.set(source.copy());
		} else {
			betterLore$milkBucket.remove();
		}
	}

	@Inject(method = "finishUsingItem", at = @At("RETURN"))
	private void betterLore$restoreMilkBucket(
			net.minecraft.world.level.Level level,
			LivingEntity consumer,
			CallbackInfoReturnable<ItemStack> cir
	) {
		ItemStack source = betterLore$milkBucket.get();
		betterLore$milkBucket.remove();
		ItemStack returned = cir.getReturnValue();
		if (source != null && returned.is(Items.BUCKET)) {
			BucketItemText.onEmptiedBucket(
					source,
					returned,
					EntityItemText.registryAccess(consumer)
			);
		}
	}
}
