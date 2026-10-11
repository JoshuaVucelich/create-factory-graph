package com.vws.createfactorygraph.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.vws.createfactorygraph.logistics.LogisticsCache;
import com.vws.createfactorygraph.profile.AreaProfiler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;

/** Retracted belt funnels ask for the segment under them every tick. */
@Mixin(FunnelBlockEntity.class)
public abstract class FunnelBlockEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void cfg$begin(CallbackInfo ci) {
        AreaProfiler.funnel.begin();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void cfg$end(CallbackInfo ci) {
        AreaProfiler.funnel.end();
    }

    @Redirect(method = "determineCurrentMode", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/belt/BeltHelper;getSegmentBE(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;)Lcom/simibubi/create/content/kinetics/belt/BeltBlockEntity;"))
    private BeltBlockEntity cfg$segment(LevelAccessor level, BlockPos pos) {
        return LogisticsCache.segmentBE(level, pos);
    }
}
