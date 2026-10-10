package com.vws.createfactorygraph.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.vws.createfactorygraph.kinetics.NeighbourCache;
import com.vws.createfactorygraph.profile.AreaProfiler;

@Mixin(RotationPropagator.class)
public abstract class RotationPropagatorMixin {
    @Inject(method = "getConnectedNeighbours", at = @At("HEAD"), cancellable = true)
    private static void cfg$hit(KineticBlockEntity be, CallbackInfoReturnable<List<KineticBlockEntity>> cir) {
        AreaProfiler.neighbourScan.begin();
        List<KineticBlockEntity> hit = NeighbourCache.get(be);
        if (hit != null) {
            AreaProfiler.neighbourScan.end();
            cir.setReturnValue(hit);
        }
    }

    @Inject(method = "getConnectedNeighbours", at = @At("RETURN"))
    private static void cfg$store(KineticBlockEntity be, CallbackInfoReturnable<List<KineticBlockEntity>> cir) {
        NeighbourCache.put(be, cir.getReturnValue());
        AreaProfiler.neighbourScan.end();
    }
}
