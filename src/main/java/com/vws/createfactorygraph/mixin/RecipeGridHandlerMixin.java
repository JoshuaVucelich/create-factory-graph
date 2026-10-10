package com.vws.createfactorygraph.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import com.vws.createfactorygraph.machines.MachineCache;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

@Mixin(RecipeGridHandler.class)
public abstract class RecipeGridHandlerMixin {
    @Inject(method = "tryToApplyRecipe", at = @At("HEAD"), cancellable = true)
    private static void cfg$recipeHit(Level world, RecipeGridHandler.GroupedItems items, CallbackInfoReturnable<ItemStack> cir) {
        switch (MachineCache.lookupRecipe(items)) {
            case NONE -> cir.setReturnValue(null);
            case STACK -> cir.setReturnValue(MachineCache.copyRecipe(items));
            case MISS -> { }
        }
    }

    @Inject(method = "tryToApplyRecipe", at = @At("RETURN"))
    private static void cfg$recipeStore(Level world, RecipeGridHandler.GroupedItems items, CallbackInfoReturnable<ItemStack> cir) {
        MachineCache.storeRecipe(items, cir.getReturnValue());
    }
}
