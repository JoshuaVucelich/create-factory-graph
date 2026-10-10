package com.vws.createfactorygraph.profile;

/** Opt-in recipe-search timing. Counters stay at zero unless {@link #active} is set. */
public final class AreaProfiler {
    public static volatile boolean active;
    public static long recipeHits, recipeMisses;

    public static void reset() {
        recipeHits = recipeMisses = 0;
    }

    private AreaProfiler() {}
}
