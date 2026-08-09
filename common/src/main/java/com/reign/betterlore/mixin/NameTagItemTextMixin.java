package com.reign.betterlore.mixin;

import com.reign.betterlore.access.EntityItemTextCarrier;
import com.reign.betterlore.world.EntityItemText;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.NameTagItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Retains exact Better Lore source markup when a formatted name tag renames an entity. */
@Mixin(NameTagItem.class)
public abstract class NameTagItemTextMixin {
	@Inject(
			method = "interactLivingEntity",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;setCustomName(Lnet/minecraft/network/chat/Component;)V",
					shift = At.Shift.AFTER
			)
	)
	private void betterLore$captureNameTagText(
			ItemStack nameTag,
			Player player,
			LivingEntity target,
			InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir
	) {
		EntityItemTextCarrier carrier = (EntityItemTextCarrier) (Object) target;
		carrier.betterLore$setItemTextData(
				EntityItemText.itemTextDataFromStack(
						nameTag,
						carrier.betterLore$registryAccess()
				)
		);
	}
}
