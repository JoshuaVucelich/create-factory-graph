package com.vws.createfactorygraph.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.vws.createfactorygraph.hooks.FactoryGraphHooks;
import com.vws.createfactorygraph.hooks.KineticNodeDuck;

/**
 * Hooks Create 6.0.10's KineticBlockEntity tick + every kinetic state mutation.
 * tick() RETURN: dormancy bookkeeping. Mutators: wake dormant node / mark graph dirty.
 */
@Mixin(KineticBlockEntity.class)
public abstract class KineticBlockEntityMixin implements KineticNodeDuck {
    @Unique private boolean cfg$dormant;
    @Unique private boolean cfg$queued;
    @Unique private int cfg$quiet;
    @Unique private byte cfg$cand;

    @Override public boolean cfg$isDormant() { return cfg$dormant; }
    @Override public void cfg$setDormant(boolean d) { cfg$dormant = d; }
    @Override public int cfg$quietTicks() { return cfg$quiet; }
    @Override public void cfg$setQuietTicks(int t) { cfg$quiet = t; }
    @Override public boolean cfg$isQueued() { return cfg$queued; }
    @Override public void cfg$setQueued(boolean q) { cfg$queued = q; }
    @Override public byte cfg$candidate() { return cfg$cand; }
    @Override public void cfg$setCandidate(byte c) { cfg$cand = c; }

    @Unique
    private KineticBlockEntity cfg$self() {
        return (KineticBlockEntity) (Object) this;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void cfg$afterTick(CallbackInfo ci) {
        FactoryGraphHooks.afterKineticTick(cfg$self());
    }

    // ---- state mutations: wake ----
    @Inject(method = "onSpeedChanged", at = @At("TAIL"))
    private void cfg$onSpeedChanged(float prev, CallbackInfo ci) { FactoryGraphHooks.touch(cfg$self()); }

    @Inject(method = "setSpeed", at = @At("TAIL"))
    private void cfg$setSpeed(float s, CallbackInfo ci) { FactoryGraphHooks.touch(cfg$self()); }

    @Inject(method = "clearKineticInformation", at = @At("TAIL"))
    private void cfg$clear(CallbackInfo ci) { FactoryGraphHooks.touch(cfg$self()); }

    @Inject(method = "warnOfMovement", at = @At("TAIL"))
    private void cfg$moved(CallbackInfo ci) { FactoryGraphHooks.touch(cfg$self()); }

    // ---- topology / power: wake + graph rebuild ----
    @Inject(method = "setSource", at = @At("TAIL"))
    private void cfg$setSource(net.minecraft.core.BlockPos src, CallbackInfo ci) { FactoryGraphHooks.topology(cfg$self()); }

    @Inject(method = "removeSource", at = @At("HEAD"))
    private void cfg$removeSourceHead(CallbackInfo ci) { FactoryGraphHooks.topology(cfg$self()); }

    @Inject(method = "removeSource", at = @At("TAIL"))
    private void cfg$removeSource(CallbackInfo ci) { FactoryGraphHooks.touch(cfg$self()); }

    @Inject(method = "setNetwork", at = @At("HEAD"))
    private void cfg$setNetworkHead(Long net, CallbackInfo ci) {
        FactoryGraphHooks.networkChanged(cfg$self(), cfg$self().network);
    }

    @Inject(method = "setNetwork", at = @At("TAIL"))
    private void cfg$setNetworkTail(Long net, CallbackInfo ci) { FactoryGraphHooks.topology(cfg$self()); }

    @Inject(method = "attachKinetics", at = @At("TAIL"))
    private void cfg$attach(CallbackInfo ci) { FactoryGraphHooks.topology(cfg$self()); }

    @Inject(method = "detachKinetics", at = @At("HEAD"))
    private void cfg$detach(CallbackInfo ci) { FactoryGraphHooks.topology(cfg$self()); }

    @Inject(method = "remove", at = @At("HEAD"))
    private void cfg$remove(CallbackInfo ci) { FactoryGraphHooks.topology(cfg$self()); }
}
