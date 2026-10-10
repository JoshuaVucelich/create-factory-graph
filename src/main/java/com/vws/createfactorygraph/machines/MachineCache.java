package com.vws.createfactorygraph.machines;

import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.vws.createfactorygraph.FGConfig;
import com.vws.createfactorygraph.mixin.accessor.GroupedItemsAccessor;
import com.vws.createfactorygraph.profile.AreaProfiler;

import net.minecraft.world.item.ItemStack;

/**
 * Remembers {@code RecipeGridHandler.tryToApplyRecipe} for an identical grid.
 * A hit returns a copy of the stack Create produced. Recipe reload clears the map.
 */
public final class MachineCache {
    public enum RecipeAnswer { MISS, NONE, STACK }

    private static final class RecipeKey {
        final boolean allowRegular;
        final int[] packed;
        final int hash;

        RecipeKey(RecipeGridHandler.GroupedItems items) {
            allowRegular = AllConfigs.server().recipes.allowRegularCraftingInCrafter.get();
            Map<Pair<Integer, Integer>, ItemStack> grid = ((GroupedItemsAccessor) items).cfg$grid();
            packed = new int[grid.size() * 4];
            int i = 0;
            int h = allowRegular ? 1 : 2;
            for (Map.Entry<Pair<Integer, Integer>, ItemStack> e : grid.entrySet()) {
                ItemStack stack = e.getValue();
                int x = e.getKey().getLeft();
                int y = e.getKey().getRight();
                int item = ItemStack.hashItemAndComponents(stack);
                int count = stack.getCount();
                packed[i++] = x;
                packed[i++] = y;
                packed[i++] = item;
                packed[i++] = count;
                h = h * 31 + x;
                h = h * 31 + y;
                h = h * 31 + item;
                h = h * 31 + count;
            }
            hash = h * 31 + grid.size();
        }

        @Override
        public int hashCode() { return hash; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof RecipeKey other)) return false;
            return allowRegular == other.allowRegular && java.util.Arrays.equals(packed, other.packed);
        }
    }

    private static final class RecipeSlot {
        final ItemStack stack;
        RecipeSlot(ItemStack stack) { this.stack = stack; }
    }

    private static final Map<RecipeKey, RecipeSlot> RECIPES = new java.util.HashMap<>();

    public static void invalidateRecipes() {
        if (!com.vws.createfactorygraph.world.ServerThread.isServer()) return;
        RECIPES.clear();
    }

    public static RecipeAnswer lookupRecipe(RecipeGridHandler.GroupedItems items) {
        if (!com.vws.createfactorygraph.world.ServerThread.isServer() || !FGConfig.enabled() || !FGConfig.recipeCache()) return RecipeAnswer.MISS;
        RecipeSlot slot = RECIPES.get(new RecipeKey(items));
        if (slot == null) return RecipeAnswer.MISS;
        AreaProfiler.recipeHits++;
        return slot.stack == null ? RecipeAnswer.NONE : RecipeAnswer.STACK;
    }

    public static ItemStack copyRecipe(RecipeGridHandler.GroupedItems items) {
        RecipeSlot slot = RECIPES.get(new RecipeKey(items));
        return slot == null || slot.stack == null ? ItemStack.EMPTY : slot.stack.copy();
    }

    public static void storeRecipe(RecipeGridHandler.GroupedItems items, ItemStack result) {
        if (!com.vws.createfactorygraph.world.ServerThread.isServer() || !FGConfig.enabled() || !FGConfig.recipeCache()) return;
        RecipeKey key = new RecipeKey(items);
        if (RECIPES.containsKey(key)) return;
        RECIPES.put(key, new RecipeSlot(result == null ? null : result.copy()));
        AreaProfiler.recipeMisses++;
    }

    private MachineCache() {}
}
