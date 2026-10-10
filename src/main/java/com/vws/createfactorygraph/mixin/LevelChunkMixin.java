package com.vws.createfactorygraph.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.vws.createfactorygraph.hooks.FactoryGraphHooks;
import com.vws.createfactorygraph.world.WorldEpoch;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** If vanilla re-registers a dormant BE's ticker (blockstate change etc.), drop our dormant flag. */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Inject(method = "updateBlockEntityTicker", at = @At("TAIL"))
    private void cfg$rebound(BlockEntity be, CallbackInfo ci) {
        FactoryGraphHooks.tickerRebound(be);
    }

    /** Caches key off this. A same-state write does not bump. */
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void cfg$epoch(BlockPos pos, BlockState state, boolean isMoving, CallbackInfoReturnable<BlockState> cir) {
        BlockState previous = cir.getReturnValue();
        if (previous == state) return;
        Level level = ((LevelChunk) (Object) this).getLevel();
        if (level != null && !level.isClientSide()) WorldEpoch.bump();
    }
}
