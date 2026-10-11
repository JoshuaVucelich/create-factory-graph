package com.vws.createfactorygraph.kinetics;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.WeakHashMap;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.vws.createfactorygraph.FGConfig;
import com.vws.createfactorygraph.profile.AreaProfiler;
import com.vws.createfactorygraph.world.WorldEpoch;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Memoises {@code RotationPropagator.getConnectedNeighbours}. Connections depend only on block
 * states, so the list is dropped on the next block change or if a neighbour is removed.
 * Chunk load and unload drop only the entries that sit in that chunk or name a neighbour there.
 */
public final class NeighbourCache {
    private static final WeakHashMap<KineticBlockEntity, Slot> SLOTS = new WeakHashMap<>();
    /** Level to chunk-long to the block entities indexed in that chunk. Same {@link WeakReference} as {@link Slot#self}. */
    private static final IdentityHashMap<Level, Long2ObjectOpenHashMap<List<WeakReference<KineticBlockEntity>>>> BY_CHUNK = new IdentityHashMap<>();

    private static final class Slot {
        long epoch;
        BlockState state;
        List<KineticBlockEntity> neighbours;
        WeakReference<KineticBlockEntity> self;
        Level level;
        long[] chunks;
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
        unindex(slot);
        slot.epoch = WorldEpoch.get();
        slot.state = be.getBlockState();
        slot.neighbours = new ArrayList<>(neighbours);
        index(slot, be);
        AreaProfiler.neighbourMisses++;
    }

    public static void clear() {
        SLOTS.clear();
        BY_CHUNK.clear();
    }

    public static void evictChunk(Level level, ChunkPos pos) {
        if (level == null || pos == null) return;
        Long2ObjectOpenHashMap<List<WeakReference<KineticBlockEntity>>> map = BY_CHUNK.get(level);
        if (map == null) return;
        List<WeakReference<KineticBlockEntity>> listed = map.remove(pos.toLong());
        if (listed == null) return;
        for (int i = 0; i < listed.size(); i++) {
            KineticBlockEntity be = listed.get(i).get();
            if (be == null) continue;
            Slot slot = SLOTS.remove(be);
            if (slot != null) unindex(slot);
        }
        if (map.isEmpty()) BY_CHUNK.remove(level);
    }

    private static void index(Slot slot, KineticBlockEntity be) {
        Level level = be.getLevel();
        slot.level = level;
        slot.self = new WeakReference<>(be);
        LongOpenHashSet chunks = new LongOpenHashSet();
        addChunk(chunks, level, be);
        List<KineticBlockEntity> neighbours = slot.neighbours;
        for (int i = 0; i < neighbours.size(); i++) addChunk(chunks, level, neighbours.get(i));
        slot.chunks = chunks.toLongArray();
        Long2ObjectOpenHashMap<List<WeakReference<KineticBlockEntity>>> map = BY_CHUNK.computeIfAbsent(level, l -> new Long2ObjectOpenHashMap<>());
        for (long chunk : slot.chunks) {
            List<WeakReference<KineticBlockEntity>> list = map.get(chunk);
            if (list == null) {
                list = new ArrayList<>();
                map.put(chunk, list);
            }
            list.add(slot.self);
        }
    }

    private static void unindex(Slot slot) {
        if (slot.level == null || slot.chunks == null || slot.self == null) return;
        Long2ObjectOpenHashMap<List<WeakReference<KineticBlockEntity>>> map = BY_CHUNK.get(slot.level);
        if (map != null) {
            for (long chunk : slot.chunks) {
                List<WeakReference<KineticBlockEntity>> list = map.get(chunk);
                if (list == null) continue;
                list.remove(slot.self);
                if (list.isEmpty()) map.remove(chunk);
            }
            if (map.isEmpty()) BY_CHUNK.remove(slot.level);
        }
        slot.chunks = null;
        slot.self = null;
    }

    private static void addChunk(LongOpenHashSet chunks, Level level, KineticBlockEntity be) {
        if (be == null || be.getLevel() != level) return;
        BlockPos p = be.getBlockPos();
        chunks.add(ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4));
    }

    private NeighbourCache() {}
}
