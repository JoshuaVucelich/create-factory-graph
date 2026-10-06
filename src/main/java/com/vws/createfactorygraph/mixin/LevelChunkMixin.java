package com.vws.createfactorygraph.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.vws.createfactorygraph.hooks.FactoryGraphHooks;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/** If vanilla re-registers a dormant BE's ticker (blockstate change etc.), drop our dormant flag. */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Inject(method = "updateBlockEntityTicker", at = @At("TAIL"))
    private void cfg$rebound(BlockEntity be, CallbackInfo ci) {
        FactoryGraphHooks.tickerRebound(be);
    }
}
