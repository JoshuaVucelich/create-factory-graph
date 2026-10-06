package com.vws.createfactorygraph.cadence;

import com.vws.createfactorygraph.api.FactoryCadence;

/**
 * Cadence state of one ship, shared by every block entity of every factory graph parented to it.
 * Server thread only (writes come from the ship mod's level tick / commands).
 */
public final class ShipGate {
    public final Object key;
    public volatile FactoryCadence.Mode mode = FactoryCadence.Mode.FULL;
    public volatile int period = -1;
    public volatile int phase = -1;

    // stats
    public long deferredTicks;   // BE ticks deferred while CHEAP
    public long ranTicks;        // BE ticks actually run (normal + catch-up) for parented BEs
    public long catchUpTicks;    // extra ticks run to repay debt
    public long forfeitedTicks;  // debt dropped because it exceeded the cap (never items)
    public long cheapSince = -1;
    public int attached;         // parented BEs at last graph rebuild (approx)
    public int graphs;           // parented graphs at last rebuild (approx)

    public ShipGate(Object key) {
        this.key = key;
    }

    public void update(FactoryCadence.Mode m, int periodTicks, int phaseTick) {
        if (periodTicks > 0) period = periodTicks;
        if (phaseTick >= 0) phase = phaseTick;
        mode = m;
    }

    public int effectivePeriod(int fallback) {
        int p = period;
        return p > 0 ? p : fallback;
    }

    public int effectivePhase(int p) {
        int ph = phase;
        if (ph < 0) ph = key.hashCode() & 0x7fffffff;
        return ph % p;
    }

    @Override
    public String toString() {
        return String.format("ship %s mode=%s period=%d phase=%d graphs=%d BEs=%d | ran=%d deferred=%d caughtUp=%d forfeited=%d",
                key, mode, period, phase, graphs, attached, ranTicks, deferredTicks, catchUpTicks, forfeitedTicks);
    }
}
