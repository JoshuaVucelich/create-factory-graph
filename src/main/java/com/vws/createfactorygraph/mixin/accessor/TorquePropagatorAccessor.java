package com.vws.createfactorygraph.mixin.accessor;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.TorquePropagator;

import net.minecraft.world.level.LevelAccessor;

@Mixin(TorquePropagator.class)
public interface TorquePropagatorAccessor {
    @Accessor("networks")
    static Map<LevelAccessor, Map<Long, KineticNetwork>> cfg$getNetworks() {
        throw new AssertionError();
    }
}
