package com.reign.betterlore.mixin;

import com.reign.betterlore.access.EntityItemTextCarrier;
import com.reign.betterlore.world.CopperGolemItemText;
import com.reign.betterlore.world.PlacedItemTextStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Saves a copper golem's exact Better Lore text when it oxidizes into a statue. */
@Pseudo
@Mixin(targets = "net.minecraft.world.entity.animal.golem.CopperGolem")
public abstract class CopperGolemItemTextMixin {
	//? if >=26.2 {
	@Inject(method = "turnToStatue", at = @At("TAIL"))
	private void betterLore$rememberStatueText(ServerLevel level, CallbackInfo ci) {
		Entity golem = (Entity) (Object) this;
		if (!golem.isRemoved()) {
			return;
		}

		BlockPos pos = golem.blockPosition();
		ItemStack statueStack = level.getBlockState(pos).getBlock().asItem().getDefaultInstance();
		if (statueStack.isEmpty()) {
			return;
		}

		CopperGolemItemText.copyFromGolem(
				(EntityItemTextCarrier) golem,
				statueStack
		);
		PlacedItemTextStorage.remember(level, pos, statueStack);
	}
	//? }
}
