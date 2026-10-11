package com.vws.createfactorygraph.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.vws.createfactorygraph.hooks.FactoryGraphHooks;
import com.vws.createfactorygraph.kinetics.StressCache;
import com.vws.createfactorygraph.profile.AreaProfiler;
import com.vws.createfactorygraph.safety.Safety;

@Mixin(KineticNetwork.class)
public abstract class KineticNetworkMixin {
    @Inject(method = "add", at = @At("HEAD"))
    private void cfg$addHead(KineticBlockEntity be, CallbackInfo ci) {
        StressCache.prepareAdd(!((KineticNetwork) (Object) this).members.containsKey(be));
    }

    @Inject(method = "add", at = @At("TAIL"))
    private void cfg$add(KineticBlockEntity be, CallbackInfo ci) {
        KineticNetwork network = (KineticNetwork) (Object) this;
        StressCache.finishAdd(network, be);
        FactoryGraphHooks.networkChanged(be, network.id);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void cfg$removeHead(KineticBlockEntity be, CallbackInfo ci) {
        KineticNetwork network = (KineticNetwork) (Object) this;
        if (network.members.containsKey(be)) StressCache.onRemoving(network, be);
    }

    @Inject(method = "remove", at = @At("TAIL"))
    private void cfg$remove(KineticBlockEntity be, CallbackInfo ci) {
        FactoryGraphHooks.afterNetworkRemove((KineticNetwork) (Object) this, be);
    }

    @Inject(method = "addSilently", at = @At("TAIL"))
    private void cfg$silent(KineticBlockEntity be, float lastCapacity, float lastStress, CallbackInfo ci) {
        StressCache.invalidate((KineticNetwork) (Object) this);
    }

    @Inject(method = "initFromTE", at = @At("HEAD"))
    private void cfg$init(float maxStress, float currentStress, int members, CallbackInfo ci) {
        StressCache.invalidate((KineticNetwork) (Object) this);
    }

    @Inject(method = "updateStressFor", at = @At("HEAD"))
    private void cfg$stressFor(KineticBlockEntity be, float stress, CallbackInfo ci) {
        StressCache.onStressReplaced((KineticNetwork) (Object) this, be, stress);
    }

    @Inject(method = "updateCapacityFor", at = @At("HEAD"))
    private void cfg$capFor(KineticBlockEntity be, float capacity, CallbackInfo ci) {
        StressCache.onCapacityReplaced((KineticNetwork) (Object) this, be, capacity);
    }

    @Inject(method = "calculateStress", at = @At("HEAD"), cancellable = true)
    private void cfg$stressHit(CallbackInfoReturnable<Float> cir) {
        AreaProfiler.stressCalc.begin();
        Float hit = null;
        try {
            hit = StressCache.stress((KineticNetwork) (Object) this);
        } catch (Throwable t) {
            Safety.noteFailOpen();
            StressCache.invalidate((KineticNetwork) (Object) this);
        }
        if (hit != null) {
            AreaProfiler.stressCalc.end();
            cir.setReturnValue(hit);
        }
    }

    @Inject(method = "calculateStress", at = @At("RETURN"))
    private void cfg$stressStore(CallbackInfoReturnable<Float> cir) {
        AreaProfiler.stressCalc.end();
        Float value = cir.getReturnValue();
        if (value != null) StressCache.storeStress((KineticNetwork) (Object) this, value);
    }

    @Inject(method = "calculateCapacity", at = @At("HEAD"), cancellable = true)
    private void cfg$capHit(CallbackInfoReturnable<Float> cir) {
        AreaProfiler.stressCalc.begin();
        Float hit = null;
        try {
            hit = StressCache.capacity((KineticNetwork) (Object) this);
        } catch (Throwable t) {
            Safety.noteFailOpen();
            StressCache.invalidate((KineticNetwork) (Object) this);
        }
        if (hit != null) {
            AreaProfiler.stressCalc.end();
            cir.setReturnValue(hit);
        }
    }

    @Inject(method = "calculateCapacity", at = @At("RETURN"))
    private void cfg$capStore(CallbackInfoReturnable<Float> cir) {
        AreaProfiler.stressCalc.end();
        Float value = cir.getReturnValue();
        if (value != null) StressCache.storeCapacity((KineticNetwork) (Object) this, value);
    }
}
