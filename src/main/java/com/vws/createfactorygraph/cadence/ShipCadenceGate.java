package com.vws.createfactorygraph.cadence;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.vws.createfactorygraph.CreateFactoryGraph;
import com.vws.createfactorygraph.FGConfig;
import com.vws.createfactorygraph.api.FactoryCadence;
import com.vws.createfactorygraph.hooks.DecorativePolicy;
import com.vws.createfactorygraph.hooks.KineticNodeDuck;
import com.vws.createfactorygraph.safety.Safety;

import net.minecraft.world.level.Level;

/**
 * Tick gate for block entities of ship-parented factory graphs. Only decorative relays are
 * deferred. Belts, deployers, bearings, and every other machine run Create's own tick.
 * Debt on those is dropped, never replayed. Ground block entities never reach this class.
 */
public final class ShipCadenceGate {
    private static int budget;

    public static void beginTick() {
        budget = FGConfig.maxCatchUpPerTick();
    }

    public static int budgetRemaining() {
        return budget;
    }

    /** @return true = skip this tick (deferred). False on any failure, so Create still ticks. */
    public static boolean beforeTick(Level level, SmartBlockEntity be, CadenceDuck duck) {
        try {
            return beforeTick0(level, be, duck);
        } catch (Throwable t) {
            Safety.noteFailOpen();
            CreateFactoryGraph.LOGGER.warn("cadence gate failed open at {}: {}", be.getBlockPos(), t.toString());
            dropGate(duck);
            return false;
        }
    }

    private static boolean beforeTick0(Level level, SmartBlockEntity be, CadenceDuck duck) {
        byte kind = kindOf(be);
        if (!Safety.mayDefer(kind, FGConfig.killed(), FGConfig.shipCadence())) {
            dropGate(duck);
            return false;
        }
        ShipGate gate = duck.cfg$gate();
        if (gate == null) return false;
        if (FactoryCadence.hasLocator() && duck.cfg$seenEpoch() != FactoryCadence.shipEpoch()) {
            duck.cfg$setSeenEpoch(FactoryCadence.shipEpoch());
            Object here = FactoryCadence.locateShip(level, be.getBlockPos().asLong());
            if (!Safety.sameKey(gate.key, here)) {
                Safety.noteSplit();
                dropGate(duck);
                return false;
            }
        }
        int debt = duck.cfg$debt();
        if (gate.mode == FactoryCadence.Mode.CHEAP && settled(level, gate)) {
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
            if (policy == FGConfig.CheapPolicy.BURST && debt > 0) repay(be, duck, gate, debt);
            gate.ranTicks++;
            return false;
        }
        if (debt > 0) repay(be, duck, gate, debt);
        gate.ranTicks++;
        return false;
    }

    private static byte kindOf(SmartBlockEntity be) {
        if (!(be instanceof KineticBlockEntity kinetic)) return DecorativePolicy.NEVER;
        KineticNodeDuck node = (KineticNodeDuck) kinetic;
        byte kind = node.cfg$candidate();
        if (kind == DecorativePolicy.UNKNOWN) {
            kind = DecorativePolicy.classify(kinetic);
            node.cfg$setCandidate(kind);
        }
        return kind;
    }

    private static void dropGate(CadenceDuck duck) {
        int debt = duck.cfg$debt();
        duck.cfg$setDebt(0);
        duck.cfg$setGate(null);
        Safety.noteForfeit(debt);
    }

    private static void repay(SmartBlockEntity be, CadenceDuck duck, ShipGate gate, int debt) {
        int n = Safety.replayTicks(Safety.RELAY, debt, budget);
        if (n <= 0) return;
        budget -= n;
        runExtra(be, duck, gate, debt, n);
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
            Safety.noteFailOpen();
            CreateFactoryGraph.LOGGER.warn("cadence catch-up failed open at {} ({}); dropping its debt", be.getBlockPos(), t.toString());
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
