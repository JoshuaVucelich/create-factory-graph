package com.vws.createfactorygraph.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.content.kinetics.KineticNetwork;

@Mixin(KineticNetwork.class)
public interface KineticNetworkAccessor {
    @Accessor("currentStress")
    float cfg$getCurrentStress();

    @Accessor("currentCapacity")
    float cfg$getCurrentCapacity();
}
