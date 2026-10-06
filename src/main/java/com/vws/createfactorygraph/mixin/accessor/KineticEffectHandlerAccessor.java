package com.vws.createfactorygraph.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.content.kinetics.base.KineticEffectHandler;

@Mixin(KineticEffectHandler.class)
public interface KineticEffectHandlerAccessor {
    @Accessor("particleSpawnCountdown")
    int cfg$getParticleSpawnCountdown();
}
