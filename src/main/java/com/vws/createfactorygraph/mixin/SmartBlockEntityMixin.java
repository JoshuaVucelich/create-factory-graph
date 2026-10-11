package com.vws.createfactorygraph.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.vws.createfactorygraph.cadence.CadenceDuck;
import com.vws.createfactorygraph.cadence.ShipGate;
import com.vws.createfactorygraph.hooks.FactoryGraphHooks;

@Mixin(SmartBlockEntity.class)
public abstract class SmartBlockEntityMixin implements CadenceDuck {
    @Unique private ShipGate cfg$shipGate;
    @Unique private int cfg$cadenceDebt;
    /** Not written by write()/saveAdditional. A restart always resumes with zero debt. */
    @Unique private int cfg$seenEpoch;

    @Override public ShipGate cfg$gate() { return cfg$shipGate; }
    @Override public void cfg$setGate(ShipGate gate) { cfg$shipGate = gate; }
    @Override public int cfg$debt() { return cfg$cadenceDebt; }
    @Override public void cfg$setDebt(int debt) { cfg$cadenceDebt = debt; }
    @Override public int cfg$seenEpoch() { return cfg$seenEpoch; }
    @Override public void cfg$setSeenEpoch(int epoch) { cfg$seenEpoch = epoch; }

    @Inject(method = "invalidate", at = @At("HEAD"))
    private void cfg$invalidate(CallbackInfo ci) {
        FactoryGraphHooks.invalidated((SmartBlockEntity) (Object) this);
    }
}
