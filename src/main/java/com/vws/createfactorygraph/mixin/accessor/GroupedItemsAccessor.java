package com.vws.createfactorygraph.mixin.accessor;

import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;

import net.minecraft.world.item.ItemStack;

@Mixin(RecipeGridHandler.GroupedItems.class)
public interface GroupedItemsAccessor {
    @Accessor("grid")
    Map<Pair<Integer, Integer>, ItemStack> cfg$grid();
}
