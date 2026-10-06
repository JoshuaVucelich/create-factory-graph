package com.vws.createfactorygraph.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticEffectHandler;
import com.vws.createfactorygraph.hooks.FactoryGraphHooks;

/** Rotation-indicator particles are counted down in tick(); a dormant relay must wake for them. */
@Mixin(KineticEffectHandler.class)
public abstract class KineticEffectHandlerMixin {
    @Shadow KineticBlockEntity kte;

    @Inject(method = "queueRotationIndicators", at = @At("TAIL"))
    private void cfg$queued(CallbackInfo ci) {
        if (kte != null) FactoryGraphHooks.touch(kte);
    }
}
