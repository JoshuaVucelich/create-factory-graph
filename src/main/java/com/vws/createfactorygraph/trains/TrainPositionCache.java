package com.vws.createfactorygraph.trains;

import java.util.WeakHashMap;

import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.vws.createfactorygraph.FGConfig;
import com.vws.createfactorygraph.profile.AreaProfiler;
import com.vws.createfactorygraph.world.WorldEpoch;

import net.minecraft.world.phys.Vec3;

/**
 * {@code TravellingPoint.getPosition} is a pure function of the point's edge, position, offset
 * and flip. Collision scans ask for the same positions many times a tick; the Vec3 is reused.
 */
public final class TrainPositionCache {
    private static final WeakHashMap<TravellingPoint, Slot> SLOTS = new WeakHashMap<>();
    private static long seenEpoch = Long.MIN_VALUE;

    private static final class Slot {
        TrackEdge edge;
        TrackGraph graph;
        long posBits;
        long offBits;
        boolean flip;
        Vec3 value;
    }

    public static void clear() {
        SLOTS.clear();
        seenEpoch = Long.MIN_VALUE;
    }

    private static void rollEpoch() {
        long now = WorldEpoch.get();
        if (now != seenEpoch) {
            seenEpoch = now;
            SLOTS.clear();
        }
    }

    public static Vec3 get(TravellingPoint point, TrackGraph graph, double offset, boolean flip) {
        if (!com.vws.createfactorygraph.world.ServerThread.isServer() || !FGConfig.enabled() || !FGConfig.trainPositionCache()) return null;
        rollEpoch();
        Slot slot = SLOTS.get(point);
        if (slot == null || slot.value == null) return null;
        if (slot.edge != point.edge || slot.graph != graph || slot.flip != flip) return null;
        if (slot.posBits != Double.doubleToRawLongBits(point.position)) return null;
        if (slot.offBits != Double.doubleToRawLongBits(offset)) return null;
        AreaProfiler.trainPosHits++;
        return slot.value;
    }

    public static void put(TravellingPoint point, TrackGraph graph, double offset, boolean flip, Vec3 value) {
        if (!com.vws.createfactorygraph.world.ServerThread.isServer() || !FGConfig.enabled() || !FGConfig.trainPositionCache() || value == null || point.edge == null) return;
        rollEpoch();
        Slot slot = SLOTS.get(point);
        if (slot != null && slot.value == value) return;
        if (slot == null) {
            slot = new Slot();
            SLOTS.put(point, slot);
        }
        slot.edge = point.edge;
        slot.graph = graph;
        slot.posBits = Double.doubleToRawLongBits(point.position);
        slot.offBits = Double.doubleToRawLongBits(offset);
        slot.flip = flip;
        slot.value = value;
        AreaProfiler.trainPosMisses++;
    }

    private TrainPositionCache() {}
}
