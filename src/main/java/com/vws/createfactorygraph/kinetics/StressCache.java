package com.vws.createfactorygraph.kinetics;

import java.util.WeakHashMap;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.vws.createfactorygraph.FGConfig;
import com.vws.createfactorygraph.profile.AreaProfiler;
import com.vws.createfactorygraph.safety.Safety;

/**
 * Incremental stress and capacity totals. The stored total is whatever Create's full
 * {@code calculateStress}/{@code calculateCapacity} last returned, then adjusted by the same
 * {@code base * abs(speed)} term those methods apply. A full rescan still runs whenever the
 * cache is cold, and the bench compares the two.
 */
public final class StressCache {
    public static final ThreadLocal<Boolean> FORCE_FULL = ThreadLocal.withInitial(() -> false);

    private static final WeakHashMap<KineticNetwork, Slot> SLOTS = new WeakHashMap<>();
    private static final WeakHashMap<KineticBlockEntity, Float> OLD_THEO = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> ADD_NEW = ThreadLocal.withInitial(() -> false);

    private static final class Slot {
        boolean stressValid;
        boolean capacityValid;
        float stress;
        float capacity;
    }

    private static boolean active() {
        return FGConfig.enabled() && FGConfig.stressCache() && com.vws.createfactorygraph.world.ServerThread.isServer();
    }

    public static Float stress(KineticNetwork network) {
        if (!active() || FORCE_FULL.get()) return null;
        Slot slot = SLOTS.get(network);
        if (slot == null || !slot.stressValid) return null;
        AreaProfiler.stressHits++;
        return slot.stress;
    }

    public static Float capacity(KineticNetwork network) {
        if (!active() || FORCE_FULL.get()) return null;
        Slot slot = SLOTS.get(network);
        if (slot == null || !slot.capacityValid) return null;
        AreaProfiler.stressHits++;
        return slot.capacity;
    }

    public static void storeStress(KineticNetwork network, float value) {
        if (!active() || FORCE_FULL.get()) return;
        Slot slot = slot(network);
        if (slot.stressValid && Float.floatToIntBits(slot.stress) == Float.floatToIntBits(value)) return;
        slot.stress = value;
        slot.stressValid = true;
        AreaProfiler.stressMisses++;
    }

    public static void storeCapacity(KineticNetwork network, float value) {
        if (!active() || FORCE_FULL.get()) return;
        Slot slot = slot(network);
        if (slot.capacityValid && Float.floatToIntBits(slot.capacity) == Float.floatToIntBits(value)) return;
        slot.capacity = value;
        slot.capacityValid = true;
        AreaProfiler.stressMisses++;
    }

    public static void invalidate(KineticNetwork network) {
        if (!com.vws.createfactorygraph.world.ServerThread.isServer()) return;
        Slot slot = SLOTS.get(network);
        if (slot != null) {
            slot.stressValid = false;
            slot.capacityValid = false;
        }
    }

    public static void beforeSpeed(KineticBlockEntity be) {
        if (!active()) return;
        OLD_THEO.put(be, be.getTheoreticalSpeed());
    }

    public static void afterSpeed(KineticBlockEntity be) {
        if (!active()) return;
        Float old = OLD_THEO.remove(be);
        if (old == null || be.network == null) return;
        float neu = be.getTheoreticalSpeed();
        if (old.floatValue() == neu) return;
        KineticNetwork network = networkOf(be);
        if (network == null) return;
        Slot slot = SLOTS.get(network);
        if (slot == null || !slot.stressValid) return;
        Float base = network.members.get(be);
        if (base == null) {
            slot.stressValid = false;
            return;
        }
        slot.stress += base * (Math.abs(neu) - Math.abs(old));
    }

    public static void prepareAdd(boolean isNew) {
        ADD_NEW.set(isNew);
    }

    public static void finishAdd(KineticNetwork network, KineticBlockEntity be) {
        if (Boolean.TRUE.equals(ADD_NEW.get())) onAdded(network, be);
        ADD_NEW.set(false);
    }

    public static void onAdded(KineticNetwork network, KineticBlockEntity be) {
        if (!active()) return;
        Slot slot = SLOTS.get(network);
        if (slot == null) return;
        Float base = network.members.get(be);
        if (base == null) {
            slot.stressValid = false;
            slot.capacityValid = false;
            return;
        }
        if (slot.stressValid) slot.stress += base * Math.abs(be.getTheoreticalSpeed());
        if (slot.capacityValid && be.isSource()) {
            Float cap = network.sources.get(be);
            if (cap == null) slot.capacityValid = false;
            else slot.capacity += cap * Math.abs(be.getGeneratedSpeed());
        }
    }

    /**
     * Drop the cached totals. Callers such as {@code removeSource} zero the speed before
     * {@code KineticNetwork.remove}, so subtracting the speed visible at remove time would
     * leave the old contribution in the total.
     */
    public static void onRemoving(KineticNetwork network, KineticBlockEntity be) {
        invalidate(network);
    }

    public static void invalidateAll() {
        if (!com.vws.createfactorygraph.world.ServerThread.isServer()) return;
        SLOTS.clear();
    }

    /** Called at the start of updateStressFor, before the members map changes. */
    public static void onStressReplaced(KineticNetwork network, KineticBlockEntity be, float newBase) {
        if (!active()) return;
        Slot slot = SLOTS.get(network);
        if (slot == null || !slot.stressValid) return;
        Float old = network.members.get(be);
        if (old == null) {
            slot.stressValid = false;
            return;
        }
        float mul = Math.abs(be.getTheoreticalSpeed());
        slot.stress += (newBase - old) * mul;
    }

    public static void onCapacityReplaced(KineticNetwork network, KineticBlockEntity be, float newCap) {
        if (!active()) return;
        Slot slot = SLOTS.get(network);
        if (slot == null || !slot.capacityValid) return;
        Float old = network.sources.get(be);
        float mul = Math.abs(be.getGeneratedSpeed());
        if (old == null) slot.capacity += newCap * mul;
        else slot.capacity += (newCap - old) * mul;
    }

    public static void invalidateBE(KineticBlockEntity be) {
        if (be.network == null) return;
        KineticNetwork network = networkOf(be);
        if (network != null) invalidate(network);
    }

    /** Full scan vs cache. Returns null if there is no network. */
    public static String audit(KineticNetwork network) {
        if (network == null) return "no-network";
        Float cached = stress(network);
        FORCE_FULL.set(true);
        float full;
        try {
            invalidate(network);
            full = network.calculateStress();
        } finally {
            FORCE_FULL.set(false);
        }
        // calculateStress's RETURN stored the full value again
        float after = network.calculateStress();
        boolean match = Float.floatToIntBits(full) == Float.floatToIntBits(after);
        return "stress full " + full + " cached-after " + after + " prior " + cached + (match ? " MATCH" : " MISMATCH");
    }

    private static KineticNetwork networkOf(KineticBlockEntity be) {
        if (!(be.getLevel() instanceof net.minecraft.world.level.LevelAccessor level) || be.network == null) return null;
        try {
            var nets = com.vws.createfactorygraph.mixin.accessor.TorquePropagatorAccessor.cfg$getNetworks().get(level);
            return nets == null ? null : nets.get(be.network);
        } catch (Throwable t) {
            Safety.noteFailOpen();
            return null;
        }
    }

    private static Slot slot(KineticNetwork network) {
        return SLOTS.computeIfAbsent(network, n -> new Slot());
    }

    private StressCache() {}
}
