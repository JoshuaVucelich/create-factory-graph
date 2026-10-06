package com.vws.createfactorygraph.hooks;

/** Injected into Create's KineticBlockEntity by KineticBlockEntityMixin. */
public interface KineticNodeDuck {
    boolean cfg$isDormant();
    void cfg$setDormant(boolean dormant);
    int cfg$quietTicks();
    void cfg$setQuietTicks(int ticks);
    boolean cfg$isQueued();
    void cfg$setQueued(boolean queued);
    /** 0 = unknown, 1 = relay candidate, 2 = belt-segment candidate, 3 = never. */
    byte cfg$candidate();
    void cfg$setCandidate(byte c);
}
