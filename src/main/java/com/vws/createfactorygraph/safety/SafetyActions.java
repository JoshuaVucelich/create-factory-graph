package com.vws.createfactorygraph.safety;

import com.vws.createfactorygraph.contraptions.ContraptionShapeCache;
import com.vws.createfactorygraph.graph.FactoryGraphManager;
import com.vws.createfactorygraph.kinetics.NeighbourCache;
import com.vws.createfactorygraph.kinetics.StressCache;
import com.vws.createfactorygraph.logistics.LogisticsCache;
import com.vws.createfactorygraph.machines.MachineCache;
import com.vws.createfactorygraph.trains.TrainPositionCache;
import com.vws.createfactorygraph.world.WorldEpoch;

/** Drops in-memory cadence and cache state. Nothing here writes a chunk. */
public final class SafetyActions {
    public static void clearCaches() {
        StressCache.invalidateAll();
        NeighbourCache.clear();
        LogisticsCache.clear();
        ContraptionShapeCache.clear();
        TrainPositionCache.clear();
        MachineCache.invalidateRecipes();
        WorldEpoch.bump();
    }

    public static void disarmAll() {
        for (FactoryGraphManager manager : FactoryGraphManager.all()) manager.disarm();
        clearCaches();
    }

    private SafetyActions() {}
}
