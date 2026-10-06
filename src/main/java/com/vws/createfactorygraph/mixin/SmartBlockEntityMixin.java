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

    @Override public ShipGate cfg$gate() { return cfg$shipGate; }
    @Override public void cfg$setGate(ShipGate gate) { cfg$shipGate = gate; }
    @Override public int cfg$debt() { return cfg$cadenceDebt; }
    @Override public void cfg$setDebt(int debt) { cfg$cadenceDebt = debt; }

    @Inject(method = "invalidate", at = @At("HEAD"))
    private void cfg$invalidate(CallbackInfo ci) {
        FactoryGraphHooks.invalidated((SmartBlockEntity) (Object) this);
    }
}
