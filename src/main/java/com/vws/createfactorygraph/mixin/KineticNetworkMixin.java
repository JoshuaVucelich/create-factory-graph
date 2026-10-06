package com.vws.createfactorygraph.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.vws.createfactorygraph.hooks.FactoryGraphHooks;

@Mixin(KineticNetwork.class)
public abstract class KineticNetworkMixin {
    @Inject(method = "add", at = @At("TAIL"))
    private void cfg$add(KineticBlockEntity be, CallbackInfo ci) {
        FactoryGraphHooks.networkChanged(be, ((KineticNetwork) (Object) this).id);
    }

    @Inject(method = "remove", at = @At("TAIL"))
    private void cfg$remove(KineticBlockEntity be, CallbackInfo ci) {
        FactoryGraphHooks.afterNetworkRemove((KineticNetwork) (Object) this, be);
    }
}
