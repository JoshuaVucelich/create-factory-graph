package com.vws.createfactorygraph.safety;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SafetyTest {
    private static final Object SHIP_A = "ship-a";
    private static final Object SHIP_B = "ship-b";

    @Test
    void unanimousShipParents() {
        Safety.ParentChoice choice = Safety.choose(new Object[] {SHIP_A, SHIP_A, SHIP_A}, 3, 64);
        assertEquals(Safety.Parenting.PARENT, choice.kind);
        assertEquals(SHIP_A, choice.ship);
    }

    @Test
    void splitGroupRefuses() {
        Safety.ParentChoice choice = Safety.choose(new Object[] {SHIP_A, SHIP_B}, 2, 64);
        assertEquals(Safety.Parenting.REFUSE, choice.kind);
    }

    @Test
    void groundMixedWithShipRefuses() {
        Safety.ParentChoice choice = Safety.choose(new Object[] {SHIP_A, null}, 2, 64);
        assertEquals(Safety.Parenting.REFUSE, choice.kind);
    }

    @Test
    void allGroundStaysGround() {
        assertEquals(Safety.Parenting.GROUND, Safety.choose(new Object[] {null, null}, 2, 64).kind);
    }

    @Test
    void oversizedNetworkRefusesWithoutParenting() {
        assertEquals(Safety.Parenting.REFUSE, Safety.choose(new Object[] {SHIP_A}, 9000, 8192).kind);
    }

    @Test
    void emptySampleRefuses() {
        assertEquals(Safety.Parenting.REFUSE, Safety.choose(new Object[0], 4, 64).kind);
        assertEquals(Safety.Parenting.REFUSE, Safety.choose(null, 4, 64).kind);
    }

    @Test
    void splitAcrossTheSampledEndsRefuses() {
        int[] idx = Safety.sampleIndices(50, 16);
        Object[] samples = new Object[idx.length];
        for (int i = 0; i < idx.length; i++) samples[i] = idx[i] < 40 ? "live" : "frozen";
        assertEquals(Safety.Parenting.REFUSE, Safety.choose(samples, 50, 8192).kind);
    }

    @Test
    void samplesCoverBothEnds() {
        assertArrayEquals(new int[] {0, 1, 2, 3, 4}, Safety.sampleIndices(5, 16));
        int[] wide = Safety.sampleIndices(100, 16);
        assertTrue(wide.length <= 16);
        assertEquals(0, wide[0]);
        assertEquals(99, wide[wide.length - 1]);
        for (int i = 1; i < wide.length; i++) assertTrue(wide[i] > wide[i - 1]);
    }

    @Test
    void onlyRelaysDefer() {
        assertTrue(Safety.mayDefer(Safety.RELAY, false, true));
        assertFalse(Safety.mayDefer((byte) 2, false, true)); // belt segment
        assertFalse(Safety.mayDefer((byte) 3, false, true)); // machine, deployer, bearing
        assertFalse(Safety.mayDefer(Safety.RELAY, true, true)); // kill switch
        assertFalse(Safety.mayDefer(Safety.RELAY, false, false));
    }

    @Test
    void unsafeDebtIsNotReplayed() {
        assertEquals(0, Safety.replayTicks((byte) 3, 40, 64));
        assertEquals(0, Safety.replayTicks((byte) 2, 40, 64));
        assertEquals(0, Safety.replayTicks(Safety.RELAY, 40, 0));
        assertEquals(16, Safety.replayTicks(Safety.RELAY, 40, 16));
        assertEquals(4, Safety.replayTicks(Safety.RELAY, 4, 16));
    }

    @Test
    void failedHookDoesNotSkipTheOriginalTick() {
        assertFalse(Safety.skipOriginal(true, true));
        assertTrue(Safety.skipOriginal(true, false));
        assertFalse(Safety.skipOriginal(false, false));
    }

    @Test
    void cadenceAndCachesAreNotPersisted() {
        assertFalse(Safety.PERSIST_CADENCE_OR_CACHE);
    }

    @Test
    void nullShipDoesNotMatchARealShip() {
        assertFalse(Safety.sameKey(null, SHIP_A));
        assertFalse(Safety.sameKey(SHIP_A, null));
        assertTrue(Safety.sameKey(SHIP_A, SHIP_A));
        assertTrue(Safety.sameKey(null, null));
    }
}
