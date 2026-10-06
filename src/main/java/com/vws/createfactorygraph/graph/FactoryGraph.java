package com.vws.createfactorygraph.graph;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.vws.createfactorygraph.FGConfig;
import com.vws.createfactorygraph.hooks.DecorativePolicy;
import com.vws.createfactorygraph.hooks.KineticNodeDuck;
import com.vws.createfactorygraph.mixin.accessor.KineticNetworkAccessor;
import com.vws.createfactorygraph.mixin.accessor.RotationPropagatorInvoker;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * One Create kinetic network as a graph. nodes = kinetic block entities (+ logistics endpoints
 * at belt ends), edges = kinetic connections (as Create's RotationPropagator sees them) and
 * belt->endpoint item links, values = speed / stress / capacity / belt item counts.
 * Built only when the network's membership or connectivity changes.
 */
public final class FactoryGraph {
    public final long networkId;
    public final Long2ObjectOpenHashMap<GraphNode> nodes = new Long2ObjectOpenHashMap<>();
    /** Edge list as pairs of BlockPos.asLong (a, b, a, b, ...), a < b. */
    public final LongArrayList edges = new LongArrayList();
    public final EnumMap<NodeKind, Integer> kindCounts = new EnumMap<>(NodeKind.class);
    public float networkStress;
    public float networkCapacity;
    public long builtAtGameTime;
    public long buildNanos;
    public int buildCount;
    public boolean edgesSkipped;

    public FactoryGraph(long networkId) {
        this.networkId = networkId;
    }

    public static NodeKind classify(KineticBlockEntity be) {
        if (be.isSource()) return NodeKind.SOURCE;
        if (be instanceof BeltBlockEntity belt) return belt.isController() ? NodeKind.BELT_CONTROLLER : NodeKind.BELT_SEGMENT;
        byte c = ((KineticNodeDuck) be).cfg$candidate();
        if (c == DecorativePolicy.UNKNOWN) c = DecorativePolicy.classify(be);
        return c == DecorativePolicy.RELAY ? NodeKind.RELAY : NodeKind.MACHINE;
    }

    /** Rebuild from Create's live network. Never loads chunks (Create's neighbour scan checks isLoaded). */
    public void rebuild(ServerLevel level, KineticNetwork network) {
        long t0 = System.nanoTime();
        nodes.clear();
        edges.clear();
        kindCounts.clear();
        edgesSkipped = false;
        KineticNetworkAccessor acc = (KineticNetworkAccessor) network;
        networkStress = acc.cfg$getCurrentStress();
        networkCapacity = acc.cfg$getCurrentCapacity();

        for (Map.Entry<KineticBlockEntity, Float> e : network.members.entrySet()) {
            KineticBlockEntity be = e.getKey();
            if (be.isRemoved() || be.getLevel() != level) continue;
            NodeKind kind = classify(be);
            GraphNode n = new GraphNode(be.getBlockPos().immutable(), kind,
                    BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString());
            n.speed = be.getSpeed();
            n.stressImpact = e.getValue() == null ? 0 : e.getValue();
            Float cap = network.sources.get(be);
            n.capacity = cap == null ? 0 : cap;
            n.dormant = ((KineticNodeDuck) be).cfg$isDormant();
            if (kind == NodeKind.BELT_CONTROLLER) {
                BeltInventory inv = ((BeltBlockEntity) be).getInventory();
                n.inventoryItems = 0;
                if (inv != null) inv.getTransportedItems().forEach(t -> n.inventoryItems += t.stack.getCount());
            }
            nodes.put(n.pos.asLong(), n);
            kindCounts.merge(kind, 1, Integer::sum);
        }

        if (nodes.size() <= FGConfig.maxEdgeNodes()) {
            for (Map.Entry<KineticBlockEntity, Float> e : network.members.entrySet()) {
                KineticBlockEntity be = e.getKey();
                if (be.isRemoved() || be.getLevel() != level) continue;
                long a = be.getBlockPos().asLong();
                List<KineticBlockEntity> nbs = RotationPropagatorInvoker.cfg$getConnectedNeighbours(be);
                for (KineticBlockEntity nb : nbs) {
                    long b = nb.getBlockPos().asLong();
                    if (a < b && nodes.containsKey(b)) {
                        edges.add(a);
                        edges.add(b);
                    }
                }
                if (be instanceof BeltBlockEntity belt && belt.isController() && belt.beltLength > 0) {
                    linkBeltEnd(level, belt, a, BeltHelper.getPositionForOffset(belt, -1));
                    linkBeltEnd(level, belt, a, BeltHelper.getPositionForOffset(belt, belt.beltLength));
                }
            }
        } else {
            edgesSkipped = true;
        }
        builtAtGameTime = level.getGameTime();
        buildCount++;
        buildNanos = System.nanoTime() - t0;
    }

    private void linkBeltEnd(ServerLevel level, BeltBlockEntity belt, long beltPos, BlockPos end) {
        if (!level.isLoaded(end)) return;
        BlockEntity target = level.getBlockEntity(end);
        if (!(target instanceof SmartBlockEntity) || target instanceof KineticBlockEntity) return;
        long key = end.asLong();
        if (!nodes.containsKey(key)) {
            GraphNode n = new GraphNode(end.immutable(), NodeKind.LOGISTICS,
                    BuiltInRegistries.BLOCK.getKey(target.getBlockState().getBlock()).toString());
            nodes.put(key, n);
            kindCounts.merge(NodeKind.LOGISTICS, 1, Integer::sum);
        }
        edges.add(Math.min(beltPos, key));
        edges.add(Math.max(beltPos, key));
    }

    public int edgeCount() {
        return edges.size() / 2;
    }

    public String summary() {
        return "net " + networkId + ": " + nodes.size() + " nodes, " + edgeCount() + " edges"
                + (edgesSkipped ? " (edges skipped: too large)" : "")
                + ", stress " + String.format("%.0f/%.0f su", networkStress, networkCapacity)
                + ", kinds " + kindCounts + ", builds " + buildCount
                + String.format(", last build %.2f ms", buildNanos / 1e6);
    }
}
