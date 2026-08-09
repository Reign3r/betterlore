package com.reign.betterlore.mixin;

import com.reign.betterlore.world.PlacedItemTextStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps placed Better Lore text attached to a dragon egg when it teleports. */
@Mixin(DragonEggBlock.class)
public abstract class DragonEggBlockMixin {
	@Unique
	private static final ThreadLocal<TeleportContext> betterLore$teleportContext = new ThreadLocal<>();

	@Inject(method = "teleport", at = @At("HEAD"))
	private void betterLore$captureTeleport(
			BlockState state,
			Level level,
			BlockPos source,
			CallbackInfo ci
	) {
		if (level instanceof ServerLevel serverLevel) {
			betterLore$teleportContext.set(new TeleportContext(serverLevel, source.asLong()));
		} else {
			betterLore$teleportContext.remove();
		}
	}

	@ModifyArg(
			method = "teleport",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
			),
			index = 0
	)
	private BlockPos betterLore$moveTeleportedText(BlockPos destination) {
		TeleportContext context = betterLore$teleportContext.get();
		betterLore$teleportContext.remove();
		if (context != null) {
			PlacedItemTextStorage.move(context.level(), context.source(), destination.asLong());
		}
		return destination;
	}

	@Inject(method = "teleport", at = @At("RETURN"))
	private void betterLore$clearTeleport(
			BlockState state,
			Level level,
			BlockPos source,
			CallbackInfo ci
	) {
		betterLore$teleportContext.remove();
	}

	private record TeleportContext(ServerLevel level, long source) {
	}
}
