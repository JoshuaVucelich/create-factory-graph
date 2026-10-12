package com.vws.benchprobe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.vws.benchprobe.TickCounters;

@Mixin(KineticBlockEntity.class)
public abstract class KineticTickCounterMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void bench$tick(CallbackInfo ci) {
        TickCounters.kineticTicks++;
    }
}
