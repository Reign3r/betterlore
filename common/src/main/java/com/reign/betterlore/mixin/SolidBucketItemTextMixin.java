package com.reign.betterlore.mixin;

import com.reign.betterlore.world.BucketItemText;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserves a powder-snow bucket's text when placement replaces it with a bucket. */
@Mixin(SolidBucketItem.class)
public abstract class SolidBucketItemTextMixin {
	@Unique
	private static final ThreadLocal<ItemStack> betterLore$sourceBucket = new ThreadLocal<>();

	@Inject(method = "useOn", at = @At("HEAD"))
	private void betterLore$captureSource(
			UseOnContext context,
			CallbackInfoReturnable<InteractionResult> cir
	) {
		ItemStack source = context.getItemInHand();
		if (source.is(Items.POWDER_SNOW_BUCKET)) {
			betterLore$sourceBucket.set(source.copy());
		} else {
			betterLore$sourceBucket.remove();
		}
	}

	@ModifyArg(
			method = "useOn",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/Player;setItemInHand(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;)V"
			),
			index = 1
	)
	private ItemStack betterLore$restoreEmptyBucket(ItemStack replacement) {
		ItemStack source = betterLore$sourceBucket.get();
		if (source != null && replacement.is(Items.BUCKET)) {
			BucketItemText.onEmptiedBucket(
					source,
					replacement,
					RegistryAccess.EMPTY
			);
		}
		return replacement;
	}

	@Inject(method = "useOn", at = @At("RETURN"))
	private void betterLore$clearSource(
			UseOnContext context,
			CallbackInfoReturnable<InteractionResult> cir
	) {
		betterLore$sourceBucket.remove();
	}
}
