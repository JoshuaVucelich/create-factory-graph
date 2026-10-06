package com.vws.createfactorygraph.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.vws.createfactorygraph.hooks.FactoryGraphHooks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/** Create sets updateSpeed=true here (contraption assembly/disassembly); a dormant relay must tick to re-attach. */
@Mixin(KineticBlock.class)
public abstract class KineticBlockMixin {
    @Inject(method = "updateIndirectNeighbourShapes", at = @At("TAIL"))
    private void cfg$shapes(BlockState state, LevelAccessor level, BlockPos pos, int flags, int count, CallbackInfo ci) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof KineticBlockEntity kbe) {
            FactoryGraphHooks.topology(kbe);
        }
    }
}
