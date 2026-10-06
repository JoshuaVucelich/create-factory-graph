package com.vws.createfactorygraph.graph;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.vws.createfactorygraph.CreateFactoryGraph;
import com.vws.createfactorygraph.FGConfig;
import com.vws.createfactorygraph.hooks.DecorativePolicy;
import com.vws.createfactorygraph.hooks.KineticNodeDuck;
import com.vws.createfactorygraph.mixin.accessor.BeltInventoryAccessor;
import com.vws.createfactorygraph.mixin.accessor.KineticBlockEntityAccessor;
import com.vws.createfactorygraph.mixin.accessor.LevelChunkInvoker;
import com.vws.createfactorygraph.mixin.accessor.TorquePropagatorAccessor;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Per-level graph state. Called from LevelTickEvent.Post ("advance the graph"):
 *  - put queued settled relays/belt segments to sleep (remove their BE ticker)
 *  - run Create's source validation for dormant nodes at Create's own cadence, bucketed
 *  - rebuild graphs for networks whose topology/power changed this tick
 * Everything is driven by Create's own state-change methods via mixins; nothing polls blocks.
 * Never loads or force-loads chunks (getChunkNow only).
 */
public final class FactoryGraphManager {
    private static final Map<ServerLevel, FactoryGraphManager> MANAGERS = new IdentityHashMap<>();

    public static FactoryGraphManager get(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level, FactoryGraphManager::new);
    }

    public static void remove(ServerLevel level) {
        FactoryGraphManager m = MANAGERS.remove(level);
        if (m != null) m.clear();
    }

    public static void clearAll() {
        MANAGERS.values().forEach(FactoryGraphManager::clear);
        MANAGERS.clear();
    }

    public static Iterable<FactoryGraphManager> all() {
        return new ArrayList<>(MANAGERS.values());
    }

    public final ServerLevel level;
    public final Long2ObjectOpenHashMap<FactoryGraph> graphs = new Long2ObjectOpenHashMap<>();
    private final LongLinkedOpenHashSet dirty = new LongLinkedOpenHashSet();
    private final List<KineticBlockEntity> sleepQueue = new ArrayList<>();
    private ReferenceOpenHashSet<KineticBlockEntity>[] buckets;
    private int dormantCount;
    private long tickCounter;
    public final ReferenceOpenHashSet<BeltInventory> jammedBelts = new ReferenceOpenHashSet<>();

    // stats
    public long sleeps, wakes, rebuilds, skippedBeTicks, validations, rebounds;
    public long beltBackoffSkips, beltJamEvents, beltResumes;
    public long advanceTicks;

    private FactoryGraphManager(ServerLevel level) {
        this.level = level;
        initBuckets();
    }

    @SuppressWarnings("unchecked")
    private void initBuckets() {
        int n;
        try {
            n = Math.max(1, AllConfigs.server().kinetics.kineticValidationFrequency.get());
        } catch (Throwable t) {
            n = 60;
        }
        buckets = new ReferenceOpenHashSet[n];
        for (int i = 0; i < n; i++) buckets[i] = new ReferenceOpenHashSet<>();
    }

    private ReferenceOpenHashSet<KineticBlockEntity> bucketFor(KineticBlockEntity be) {
        return buckets[(be.getBlockPos().hashCode() & 0x7fffffff) % buckets.length];
    }

    public int dormantCount() {
        return dormantCount;
    }

    public void markDirty(long networkId) {
        if (FGConfig.enabled()) dirty.add(networkId);
    }

    public void queueSleep(KineticBlockEntity be) {
        sleepQueue.add(be);
    }

    /** End of level tick. */
    public void endTick() {
        if (!FGConfig.enabled()) {
            if (dormantCount > 0 || !sleepQueue.isEmpty()) wakeAll("disabled");
            dirty.clear();
            graphs.clear();
            return;
        }
        tickCounter++;
        advanceTicks++;

        // 1. sleep settled nodes (deferred so we never touch the ticker list mid-iteration)
        if (!sleepQueue.isEmpty()) {
            for (KineticBlockEntity be : sleepQueue) {
                KineticNodeDuck duck = (KineticNodeDuck) be;
                duck.cfg$setQueued(false);
                if (duck.cfg$isDormant()) continue;
                byte cand = duck.cfg$candidate();
                if (cand == DecorativePolicy.NEVER || cand == DecorativePolicy.UNKNOWN) continue;
                if (duck.cfg$quietTicks() < FGConfig.settleTicks()) continue;
                if (!DecorativePolicy.isQuiet(be, cand)) continue;
                if (level.getBlockEntity(be.getBlockPos()) != be) continue; // getBlockEntity on loaded pos only
                sleep(be);
            }
            sleepQueue.clear();
        }

        // 2. dormant validation at Create's cadence (each dormant node once per kineticValidationFrequency ticks)
        ReferenceOpenHashSet<KineticBlockEntity> bucket = buckets[(int) (tickCounter % buckets.length)];
        if (!bucket.isEmpty()) {
            boolean validate = FGConfig.dormantValidation();
            for (KineticBlockEntity be : new ArrayList<>(bucket)) {
                if (be.isRemoved()) {
                    forgetDormant(be);
                    continue;
                }
                if (validate) {
                    validations++;
                    ((KineticBlockEntityAccessor) be).cfg$validateKinetics();
                }
            }
        }
        skippedBeTicks += dormantCount;

        // 3. rebuild dirty graphs (bounded)
        if (!dirty.isEmpty()) {
            Map<Long, KineticNetwork> nets = TorquePropagatorAccessor.cfg$getNetworks().get(level);
            int budget = FGConfig.maxRebuildsPerTick();
            LongIterator it = dirty.iterator();
            while (it.hasNext() && budget-- > 0) {
                long id = it.nextLong();
                it.remove();
                KineticNetwork net = nets == null ? null : nets.get(id);
                if (net == null || net.members.isEmpty()) {
                    graphs.remove(id);
                    continue;
                }
                FactoryGraph g = graphs.computeIfAbsent(id, FactoryGraph::new);
                try {
                    g.rebuild(level, net);
                    rebuilds++;
                } catch (Throwable t) {
                    CreateFactoryGraph.LOGGER.warn("factory graph rebuild failed for network {}", id, t);
                    graphs.remove(id);
                }
            }
        }

        // 4. prune jam set occasionally
        if ((tickCounter & 255) == 0) jammedBelts.removeIf(inv -> ((BeltInventoryAccessor) inv).cfg$getBelt().isRemoved());
    }

    private void sleep(KineticBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        LevelChunk chunk = level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        if (chunk == null) return;
        ((LevelChunkInvoker) chunk).cfg$removeBlockEntityTicker(pos);
        ((KineticNodeDuck) be).cfg$setDormant(true);
        bucketFor(be).add(be);
        dormantCount++;
        sleeps++;
        FactoryGraph g = be.network == null ? null : graphs.get(be.network.longValue());
        if (g != null) {
            GraphNode n = g.nodes.get(pos.asLong());
            if (n != null) n.dormant = true;
        }
    }

    /** Put the BE back on the ticker list. */
    public void wake(KineticBlockEntity be, String reason) {
        KineticNodeDuck duck = (KineticNodeDuck) be;
        if (!duck.cfg$isDormant()) return;
        drop(be);
        duck.cfg$setQuietTicks(0);
        duck.cfg$setCandidate(DecorativePolicy.UNKNOWN);
        wakes++;
        if (be.isRemoved()) return;
        BlockPos pos = be.getBlockPos();
        LevelChunk chunk = level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        if (chunk == null) return; // unloading: the chunk rebuilds its tickers when it loads again
        if (chunk.getBlockEntities().get(pos) != be) return;
        ((LevelChunkInvoker) chunk).cfg$updateBlockEntityTicker(be);
        FactoryGraph g = be.network == null ? null : graphs.get(be.network.longValue());
        if (g != null) {
            GraphNode n = g.nodes.get(pos.asLong());
            if (n != null) n.dormant = false;
        }
    }

    /** BE removed/unloaded or vanilla already re-registered its ticker. */
    public void forgetDormant(KineticBlockEntity be) {
        KineticNodeDuck duck = (KineticNodeDuck) be;
        if (!duck.cfg$isDormant()) return;
        drop(be);
        duck.cfg$setQuietTicks(0);
        rebounds++;
    }

    private void drop(KineticBlockEntity be) {
        ((KineticNodeDuck) be).cfg$setDormant(false);
        if (bucketFor(be).remove(be)) dormantCount--;
        else {
            for (ReferenceOpenHashSet<KineticBlockEntity> b : buckets) {
                if (b.remove(be)) {
                    dormantCount--;
                    break;
                }
            }
        }
    }

    public void wakeAll(String reason) {
        List<KineticBlockEntity> all = new ArrayList<>();
        for (ReferenceOpenHashSet<KineticBlockEntity> b : buckets) all.addAll(b);
        for (KineticBlockEntity be : all) wake(be, reason);
        for (KineticBlockEntity be : sleepQueue) ((KineticNodeDuck) be).cfg$setQueued(false);
        sleepQueue.clear();
    }

    public void markAllKnownDirty() {
        Map<Long, KineticNetwork> nets = TorquePropagatorAccessor.cfg$getNetworks().get(level);
        if (nets != null) nets.keySet().forEach(id -> dirty.add(id.longValue()));
    }

    public int pendingDirty() {
        return dirty.size();
    }

    public int nodeTotal() {
        int n = 0;
        for (FactoryGraph g : graphs.values()) n += g.nodes.size();
        return n;
    }

    public int edgeTotal() {
        int n = 0;
        for (FactoryGraph g : graphs.values()) n += g.edgeCount();
        return n;
    }

    /** Level unload / server stop: forget flags only (never re-register tickers while unloading). */
    private void clear() {
        for (ReferenceOpenHashSet<KineticBlockEntity> b : buckets) {
            for (KineticBlockEntity be : b) ((KineticNodeDuck) be).cfg$setDormant(false);
            b.clear();
        }
        dormantCount = 0;
        for (KineticBlockEntity be : sleepQueue) ((KineticNodeDuck) be).cfg$setQueued(false);
        sleepQueue.clear();
        graphs.clear();
        dirty.clear();
        jammedBelts.clear();
    }
}
