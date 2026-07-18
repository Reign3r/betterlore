package com.reign.betterlore.mixin;

import com.reign.betterlore.world.PlacedItemTextStorage;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	@Unique
	private static final ThreadLocal<ItemStack> betterLore$placementStack = new ThreadLocal<>();

	@Inject(method = "place", at = @At("HEAD"))
	private void betterLore$capturePlacedStack(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		betterLore$placementStack.set(context.getItemInHand().copy());
	}

	@Inject(method = "place", at = @At("RETURN"))
	private void betterLore$rememberPlacedText(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		ItemStack placedStack = betterLore$placementStack.get();
		betterLore$placementStack.remove();
		if (placedStack == null || !cir.getReturnValue().consumesAction()) {
			return;
		}

		PlacedItemTextStorage.remember(context.getLevel(), context.getClickedPos(), placedStack);
	}
}
