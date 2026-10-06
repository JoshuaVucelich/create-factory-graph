package com.vws.createfactorygraph.mixin.accessor;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

@Mixin(RotationPropagator.class)
public interface RotationPropagatorInvoker {
    @Invoker("getConnectedNeighbours")
    static List<KineticBlockEntity> cfg$getConnectedNeighbours(KineticBlockEntity be) {
        throw new AssertionError();
    }
}
