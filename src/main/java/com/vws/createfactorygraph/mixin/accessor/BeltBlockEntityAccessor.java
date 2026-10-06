package com.vws.createfactorygraph.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;

import net.neoforged.neoforge.items.IItemHandler;

@Mixin(BeltBlockEntity.class)
public interface BeltBlockEntityAccessor {
    @Accessor("itemHandler")
    IItemHandler cfg$getItemHandler();
}
