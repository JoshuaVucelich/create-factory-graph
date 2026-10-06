package com.vws.createfactorygraph.cadence;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.vws.createfactorygraph.CreateFactoryGraph;
import com.vws.createfactorygraph.FGConfig;
import com.vws.createfactorygraph.api.FactoryCadence;

import net.minecraft.world.level.Level;

/**
 * Tick gate for block entities of ship-parented factory graphs. Only ever called for BEs whose
 * gate is non-null (ground BEs never reach this class).
 *
 * CHEAP:
 *  - SLOW  (default): the BE runs one normal tick per hull period, on the hull's own step phase;
 *          the other period-1 ticks become debt.
 *  - SLEEP: no ticks at all while CHEAP; every tick becomes debt.
 *  - BURST: on the hull step the BE runs its whole period of ticks back-to-back (throughput-exact,
 *          only batching; least saving).
 * Awake (FULL / REBUILDING): the BE ticks every tick and additionally repays up to
 * catchUpTicksPerTick of debt per tick until debt is 0.
 * Catch-up only ever runs Create's own SmartBlockEntity.tick(); nothing moves, ejects or drops items.
 * Debt above maxDebtTicks is forfeited (that work is simply not replayed; no items are touched).
 */
public final class ShipCadenceGate {

    /** @return true = skip this tick (deferred) */
    public static boolean beforeTick(Level level, SmartBlockEntity be, CadenceDuck duck) {
        ShipGate gate = duck.cfg$gate();
        boolean active = FGConfig.enabled() && FGConfig.shipCadence();
        int debt = duck.cfg$debt();
        if (active && gate.mode == FactoryCadence.Mode.CHEAP && settled(level, gate)) {
            FGConfig.CheapPolicy policy = FGConfig.cheapPolicy();
            int cap = FGConfig.maxDebtTicks();
            if (policy == FGConfig.CheapPolicy.SLEEP) {
                defer(gate, duck, debt, cap);
                return true;
            }
            int p = gate.effectivePeriod(FGConfig.defaultHullPeriod());
            if (p <= 1) {
                gate.ranTicks++;
                return false;
            }
            long t = level.getGameTime();
            if (t % p != gate.effectivePhase(p)) {
                defer(gate, duck, debt, cap);
                return true;
            }
            if (policy == FGConfig.CheapPolicy.BURST && debt > 0) {
                int n = Math.min(debt, p - 1 + FGConfig.catchUpTicksPerTick());
                runExtra(be, duck, gate, debt, n);
            }
            gate.ranTicks++;
            return false; // the hull-step tick itself runs normally
        }
        // awake (or feature off): repay debt gradually
        if (debt > 0) {
            int n = Math.min(debt, active ? FGConfig.catchUpTicksPerTick() : debt);
            runExtra(be, duck, gate, debt, n);
        }
        gate.ranTicks++;
        return false;
    }

    private static boolean settled(Level level, ShipGate gate) {
        long t = level.getGameTime();
        long since = gate.cheapSince;
        if (since < 0) {
            gate.cheapSince = t;
            since = t;
        }
        return t - since >= FGConfig.cheapSettleTicks();
    }

    private static void defer(ShipGate gate, CadenceDuck duck, int debt, int cap) {
        if (debt < cap) duck.cfg$setDebt(debt + 1);
        else gate.forfeitedTicks++;
        gate.deferredTicks++;
    }

    private static void runExtra(SmartBlockEntity be, CadenceDuck duck, ShipGate gate, int debt, int n) {
        int done = 0;
        try {
            for (; done < n; done++) {
                if (be.isRemoved()) break;
                be.tick();
            }
        } catch (Throwable t) {
            CreateFactoryGraph.LOGGER.warn("cadence catch-up tick failed at {} ({}); dropping its debt", be.getBlockPos(), t.toString());
            duck.cfg$setDebt(0);
            gate.catchUpTicks += done;
            gate.ranTicks += done;
            return;
        }
        duck.cfg$setDebt(be.isRemoved() ? 0 : debt - done);
        gate.catchUpTicks += done;
        gate.ranTicks += done;
    }

    private ShipCadenceGate() {}
}
