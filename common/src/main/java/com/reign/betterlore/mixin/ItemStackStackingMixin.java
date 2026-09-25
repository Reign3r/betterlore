package com.reign.betterlore.mixin;

import com.reign.betterlore.world.StackingCompatibility;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps Better Lore's private source markup out of vanilla stack identity. */
@Mixin(ItemStack.class)
public abstract class ItemStackStackingMixin {
	@Inject(method = "isSameItemSameComponents", at = @At("HEAD"), cancellable = true)
	private static void betterLore$ignorePrivateSourceData(
			ItemStack left,
			ItemStack right,
			CallbackInfoReturnable<Boolean> cir
	) {
		if (StackingCompatibility.matchesIgnoringBetterLore(left, right)) {
			cir.setReturnValue(true);
		}
	}

	/** Keep exact source changes visible to anvil/menu change detection. */
	@Inject(method = "matches", at = @At("HEAD"), cancellable = true)
	private static void betterLore$keepPrivateSourceChanges(
			ItemStack left,
			ItemStack right,
			CallbackInfoReturnable<Boolean> cir
	) {
		if (left != right
				&& left.getCount() == right.getCount()
				&& StackingCompatibility.differsOnlyByBetterLore(left, right)) {
			cir.setReturnValue(false);
		}
	}
}
