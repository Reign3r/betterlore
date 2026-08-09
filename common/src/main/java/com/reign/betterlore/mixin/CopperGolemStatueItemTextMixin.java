package com.reign.betterlore.mixin;

import com.reign.betterlore.access.EntityItemTextCarrier;
import com.reign.betterlore.world.CopperGolemItemText;
import com.reign.betterlore.world.PlacedItemTextStorage;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Restores a statue's exact Better Lore text when it awakens as a copper golem. */
@Pseudo
@Mixin(targets = "net.minecraft.world.level.block.entity.CopperGolemStatueBlockEntity")
public abstract class CopperGolemStatueItemTextMixin {
	//? if >=26.2 {
	@Inject(method = "removeStatue", at = @At("RETURN"))
	private void betterLore$restoreGolemText(CallbackInfoReturnable<Object> cir) {
		Object returned = cir.getReturnValue();
		if (!(returned instanceof Entity golem)) {
			return;
		}

		BlockEntity statue = (BlockEntity) (Object) this;
		Level level = statue.getLevel();
		if (level == null) {
			return;
		}

		ItemStack statueStack = statue.getBlockState().getBlock().asItem().getDefaultInstance();
		if (statueStack.isEmpty()) {
			return;
		}
		Component vanillaName = golem.getCustomName();
		if (vanillaName != null) {
			statueStack.set(DataComponents.CUSTOM_NAME, vanillaName);
		}

		PlacedItemTextStorage.restoreDrop(level, statue.getBlockPos(), statueStack);
		golem.setCustomName(statueStack.get(DataComponents.CUSTOM_NAME));
		EntityItemTextCarrier carrier = (EntityItemTextCarrier) golem;
		carrier.betterLore$setItemTextData(
				CopperGolemItemText.snapshotFromStatue(
						statueStack,
						carrier.betterLore$registryAccess()
				)
		);
	}
	//? }
}
