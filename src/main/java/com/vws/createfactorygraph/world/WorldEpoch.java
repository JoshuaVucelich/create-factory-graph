package com.vws.createfactorygraph.world;

/**
 * Bumped when a server chunk's block state actually changes. Caches that depend on
 * the world topology store the epoch they were built at and miss after a change.
 * Server thread only.
 */
public final class WorldEpoch {
    private static long epoch = 1;

    public static long get() {
        return epoch;
    }

    public static void bump() {
        epoch++;
    }

    private WorldEpoch() {}
}
