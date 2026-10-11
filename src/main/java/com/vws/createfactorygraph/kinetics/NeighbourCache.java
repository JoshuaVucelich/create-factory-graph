package com.vws.createfactorygraph.kinetics;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.vws.createfactorygraph.FGConfig;
import com.vws.createfactorygraph.profile.AreaProfiler;
import com.vws.createfactorygraph.world.WorldEpoch;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Memoises {@code RotationPropagator.getConnectedNeighbours}. Connections depend only on block
 * states, so the list is dropped on the next block change or if a neighbour is removed.
 */
public final class NeighbourCache {
    private static final WeakHashMap<KineticBlockEntity, Slot> SLOTS = new WeakHashMap<>();

    private static final class Slot {
        long epoch;
        BlockState state;
        List<KineticBlockEntity> neighbours;
    }

    public static List<KineticBlockEntity> get(KineticBlockEntity be) {
        if (!FGConfig.enabled() || !FGConfig.neighbourCache()) return null;
        if (be.getLevel() == null || be.getLevel().isClientSide) return null;
        Slot slot = SLOTS.get(be);
        if (slot == null || slot.epoch != WorldEpoch.get() || slot.state != be.getBlockState()) return null;
        for (int i = 0; i < slot.neighbours.size(); i++) {
            KineticBlockEntity n = slot.neighbours.get(i);
            if (n.isRemoved()) return null;
        }
        AreaProfiler.neighbourHits++;
        return slot.neighbours;
    }

    public static void put(KineticBlockEntity be, List<KineticBlockEntity> neighbours) {
        if (!FGConfig.enabled() || !FGConfig.neighbourCache()) return;
        if (be.getLevel() == null || be.getLevel().isClientSide || neighbours == null) return;
        Slot slot = SLOTS.computeIfAbsent(be, k -> new Slot());
        if (slot.neighbours == neighbours && slot.epoch == WorldEpoch.get() && slot.state == be.getBlockState()) return;
        slot.epoch = WorldEpoch.get();
        slot.state = be.getBlockState();
        slot.neighbours = new ArrayList<>(neighbours);
        AreaProfiler.neighbourMisses++;
    }

    public static void clear() {
        SLOTS.clear();
    }

    public static void evictChunk(Level level, ChunkPos pos) {
        if (level == null || pos == null) return;
        SLOTS.entrySet().removeIf(e -> {
            if (inChunk(level, e.getKey(), pos)) return true;
            List<KineticBlockEntity> neighbours = e.getValue().neighbours;
            if (neighbours == null) return false;
            for (int i = 0; i < neighbours.size(); i++) {
                if (inChunk(level, neighbours.get(i), pos)) return true;
            }
            return false;
        });
    }

    private static boolean inChunk(Level level, KineticBlockEntity be, ChunkPos pos) {
        if (be == null || be.getLevel() != level) return false;
        BlockPos p = be.getBlockPos();
        return (p.getX() >> 4) == pos.x && (p.getZ() >> 4) == pos.z;
    }

    private NeighbourCache() {}
}
