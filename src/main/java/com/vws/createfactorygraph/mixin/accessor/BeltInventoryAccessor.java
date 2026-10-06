package com.vws.createfactorygraph.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;

@Mixin(BeltInventory.class)
public interface BeltInventoryAccessor {
    @Accessor("belt")
    BeltBlockEntity cfg$getBelt();
}
