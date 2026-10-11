package com.vws.createfactorygraph.cadence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.vws.createfactorygraph.api.FactoryCadence;

class ShipGateTest {
    @Test
    void modeChangesAreTrackedPerShip() {
        ShipGate a = new ShipGate("a");
        ShipGate b = new ShipGate("b");
        a.update(FactoryCadence.Mode.CHEAP, 2, 0);
        assertEquals(1, a.epoch);
        assertEquals(0, b.epoch);
        a.update(FactoryCadence.Mode.CHEAP, 4, 1);
        assertEquals(1, a.epoch);
        b.update(FactoryCadence.Mode.REBUILDING, -1, -1);
        assertEquals(1, a.epoch);
        assertEquals(1, b.epoch);
        a.update(FactoryCadence.Mode.FULL, -1, -1);
        assertEquals(2, a.epoch);
        assertEquals(1, b.epoch);
        assertEquals(FactoryCadence.Mode.FULL, a.mode);
    }
}
