package com.reign.betterlore.mixin;

import com.reign.betterlore.world.PlacedItemTextStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps sparse placed-item text attached to blocks while pistons move them. */
@Mixin(PistonMovingBlockEntity.class)
public abstract class PistonMovingBlockEntityMixin {
	/**
	 * Set only by vanilla's runtime movement constructor. Block entities loaded
	 * from disk use the two-argument constructor, so they must not move data again.
	 */
	@Unique
	private boolean betterLore$transferOnLevelSet;

	@Inject(
			method = "<init>(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;ZZ)V",
			at = @At("RETURN")
	)
	private void betterLore$markFreshMovement(
			BlockPos pos,
			BlockState movingState,
			BlockState movedState,
			Direction direction,
			boolean extending,
			boolean sourcePiston,
			CallbackInfo ci
	) {
		// The source-piston animation does not move the piston base block.
		betterLore$transferOnLevelSet = !sourcePiston;
	}

	@Inject(
			method = "setLevel(Lnet/minecraft/world/level/Level;)V",
			at = @At("RETURN")
	)
	private void betterLore$movePlacedText(Level level, CallbackInfo ci) {
		if (!betterLore$transferOnLevelSet) {
			return;
		}
		betterLore$transferOnLevelSet = false;
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}

		PistonMovingBlockEntity movingBlock = (PistonMovingBlockEntity) (Object) this;
		long destinationPos = movingBlock.getBlockPos().asLong();
		long sourcePos = BlockPos.offset(destinationPos, movingBlock.getMovementDirection().getOpposite());
		PlacedItemTextStorage.move(serverLevel, sourcePos, destinationPos);
	}
}
