package com.reign.betterlore.mixin;

import com.reign.betterlore.world.BucketItemText;
import com.reign.betterlore.world.EntityItemText;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Carries Better Lore text across vanilla's empty-bucket to filled-bucket replacement. */
@Mixin(ItemUtils.class)
public abstract class ItemUtilsBucketItemTextMixin {
	@Inject(
			method = "createFilledResult(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;",
			at = @At("HEAD")
	)
	private static void betterLore$transferFilledBucketText(
			ItemStack source,
			Player player,
			ItemStack filled,
			boolean preventDuplicatesInCreative,
			CallbackInfoReturnable<ItemStack> cir
	) {
		if (!(source.getItem() instanceof BucketItem)
				|| (!(filled.getItem() instanceof BucketItem)
				&& !filled.is(Items.POWDER_SNOW_BUCKET)
				&& !filled.is(Items.MILK_BUCKET))) {
			return;
		}

		BucketItemText.onFilledBucket(
				source,
				filled,
				EntityItemText.registryAccess(player)
		);
	}
}
