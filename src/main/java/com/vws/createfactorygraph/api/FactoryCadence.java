package com.vws.createfactorygraph.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import com.vws.createfactorygraph.cadence.ShipGate;

/**
 * Phase 4 cross-mod cadence API. Deliberately uses only JDK types in its public signatures so
 * a ship mod can drive it by reflection with no compile/runtime dependency on this jar (and this
 * jar has none on the ship mod). Everything is optional: with no ship mod present no graph is
 * ever parented and every factory ticks exactly as in Phase 2.
 *
 * Ship mod side:
 *  - {@link #setShipLocator(BiFunction)} once: (ServerLevel, BlockPos.asLong) -> ship key or null
 *  - {@link #setShipMode(Object, Mode, int, int)} on every hull cadence change
 *    (CHEAP enter/exit, REBUILDING, FULL). The 2-arg form is kept for the Phase 3 jar.
 *
 * Factory side: graphs whose blocks the locator places on a ship get a {@link ShipGate}; their
 * block entities read the gate on every tick (one field read; ground BEs have no gate and never
 * look anything up). Ground factories never subscribe to anything.
 */
public final class FactoryCadence {
    public enum Mode { FULL, CHEAP, REBUILDING }

    public static final int API_VERSION = 2;

    private static final Map<Object, ShipGate> GATES = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<BiConsumer<Object, Mode>> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile BiFunction<Object, Long, Object> locator;

    // diagnostics
    public static volatile long eventsReceived, cheapEvents, rebuildingEvents, fullEvents;
    private static final List<String> RECENT = new ArrayList<>();

    public static int apiVersion() {
        return API_VERSION;
    }

    /** Phase-3 compatible entry point (period/phase unknown: factory uses its own default). */
    public static void setShipMode(Object shipKey, Mode mode) {
        setShipMode(shipKey, mode, -1, -1);
    }

    /**
     * @param shipKey opaque ship identity (Aeronautics: SubLevel UUID)
     * @param periodTicks hull cadence in ticks while CHEAP (e.g. 2 = 10 Hz), -1 = unknown
     * @param phase tick phase of the hull step: hull steps when gameTime % period == phase, -1 = unknown
     */
    public static void setShipMode(Object shipKey, Mode mode, int periodTicks, int phase) {
        if (shipKey == null || mode == null) return;
        ShipGate gate = GATES.computeIfAbsent(shipKey, ShipGate::new);
        Mode prev = gate.mode;
        gate.update(mode, periodTicks, phase);
        eventsReceived++;
        switch (mode) {
            case CHEAP -> cheapEvents++;
            case REBUILDING -> rebuildingEvents++;
            case FULL -> fullEvents++;
        }
        synchronized (RECENT) {
            RECENT.add(System.currentTimeMillis() % 100000000 + " " + shipKey + " " + prev + "->" + mode
                    + " period=" + gate.period + " phase=" + gate.phase + " parentedBEs=" + gate.attached);
            if (RECENT.size() > 16) RECENT.remove(0);
        }
        for (BiConsumer<Object, Mode> l : LISTENERS) {
            try { l.accept(shipKey, mode); } catch (Throwable ignored) {}
        }
    }

    public static Mode getShipMode(Object shipKey) {
        ShipGate g = shipKey == null ? null : GATES.get(shipKey);
        return g == null ? Mode.FULL : g.mode;
    }

    /** Ship mod registers how to find the ship that owns a block position (null = ground). */
    public static void setShipLocator(BiFunction<Object, Long, Object> loc) {
        locator = loc;
    }

    public static boolean hasLocator() {
        return locator != null;
    }

    /** @return ship key owning that block, or null for ground / no ship mod */
    public static Object locateShip(Object level, long packedPos) {
        BiFunction<Object, Long, Object> l = locator;
        if (l == null) return null;
        try {
            return l.apply(level, packedPos);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Optional observer hook for other mods (not used by ground factories). */
    public static void addListener(BiConsumer<Object, Mode> listener) {
        LISTENERS.add(listener);
    }

    // ---- internal (this mod) ----
    public static ShipGate gateFor(Object shipKey) {
        return GATES.computeIfAbsent(shipKey, ShipGate::new);
    }

    public static Iterable<ShipGate> gates() {
        return new ArrayList<>(GATES.values());
    }

    public static List<String> recentEvents() {
        synchronized (RECENT) {
            return new ArrayList<>(RECENT);
        }
    }

    /** Server stop: forget gates (ship mod re-announces modes on next start). Locator stays. */
    public static void reset() {
        GATES.clear();
        synchronized (RECENT) { RECENT.clear(); }
    }

    private FactoryCadence() {}
}
