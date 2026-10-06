package com.vws.createfactorygraph.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

@Mixin(LevelChunk.class)
public interface LevelChunkInvoker {
    @Invoker("removeBlockEntityTicker")
    void cfg$removeBlockEntityTicker(BlockPos pos);

    @Invoker("updateBlockEntityTicker")
    void cfg$updateBlockEntityTicker(BlockEntity be);
}
