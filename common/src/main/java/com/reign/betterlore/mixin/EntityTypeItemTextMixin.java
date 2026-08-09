package com.reign.betterlore.mixin;

import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;

//? if <1.21.5 {
import com.reign.betterlore.access.EntityItemTextCarrier;
import com.reign.betterlore.world.EntityItemText;
import net.minecraft.nbt.CompoundTag;
//? if <=1.21.1 {
import net.minecraft.server.level.ServerLevel;
//? } else {
import net.minecraft.world.level.Level;
//? }
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;
//? }

/** Captures Better Lore source data before vanilla entities retained CUSTOM_DATA. */
@Mixin(EntityType.class)
public abstract class EntityTypeItemTextMixin {
	//? if <1.21.5 {
	@Inject(method = "appendDefaultStackConfig", at = @At("RETURN"), cancellable = true)
	private static <T extends Entity> void betterLore$appendItemTextCapture(
			Consumer<T> initialConfig,
			//? if <=1.21.1 {
			ServerLevel level,
			//? } else {
			Level level,
			//? }
			ItemStack sourceStack,
			Player user,
			CallbackInfoReturnable<Consumer<T>> cir
	) {
		CompoundTag itemTextData = EntityItemText.itemTextDataFromStack(
				sourceStack,
				level.registryAccess()
		);
		if (itemTextData.isEmpty()) {
			return;
		}

		Consumer<T> vanillaConfig = cir.getReturnValue();
		cir.setReturnValue(vanillaConfig.andThen(entity ->
				((EntityItemTextCarrier) entity).betterLore$setItemTextData(itemTextData)
		));
	}
	//? }
}
