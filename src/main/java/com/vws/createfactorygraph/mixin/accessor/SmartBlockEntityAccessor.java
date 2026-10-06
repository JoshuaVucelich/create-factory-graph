package com.vws.createfactorygraph.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

@Mixin(SmartBlockEntity.class)
public interface SmartBlockEntityAccessor {
    @Accessor("initialized")
    boolean cfg$isInitialized();
}
