package com.vws.createfactorygraph.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.vws.createfactorygraph.profile.AreaProfiler;
import com.vws.createfactorygraph.trains.TrainPositionCache;

import net.minecraft.world.phys.Vec3;

@Mixin(TravellingPoint.class)
public abstract class TravellingPointMixin {
    @Inject(method = "getPositionWithOffset", at = @At("HEAD"), cancellable = true)
    private void cfg$hit(TrackGraph trackGraph, double offset, boolean flipUpsideDown, CallbackInfoReturnable<Vec3> cir) {
        AreaProfiler.trainPosition.begin();
        Vec3 hit = TrainPositionCache.get((TravellingPoint) (Object) this, trackGraph, offset, flipUpsideDown);
        if (hit != null) {
            AreaProfiler.trainPosition.end();
            cir.setReturnValue(hit);
        }
    }

    @Inject(method = "getPositionWithOffset", at = @At("RETURN"))
    private void cfg$store(TrackGraph trackGraph, double offset, boolean flipUpsideDown, CallbackInfoReturnable<Vec3> cir) {
        TrainPositionCache.put((TravellingPoint) (Object) this, trackGraph, offset, flipUpsideDown, cir.getReturnValue());
        AreaProfiler.trainPosition.end();
    }
}
