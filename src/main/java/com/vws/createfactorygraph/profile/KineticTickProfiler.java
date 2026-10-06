package com.vws.createfactorygraph.profile;

/**
 * Opt-in (/factorygraph profile start) wall-clock counter around every Create kinetic
 * block-entity tick (SmartBlockEntityTicker -> KineticBlockEntity) and belt inventory ticks,
 * plus whole-server-tick time. Used for A/B with graph mode on vs off. Server thread only.
 */
public final class KineticTickProfiler {
    public static volatile boolean active;

    private static long beStart;
    private static long tickStart;

    public static long serverTicks;
    public static long serverTickNanos;
    public static long kineticTicks;
    public static long kineticNanos;
    public static long beltInvTicks;
    public static long beltInvSkipped;
    public static long startedAtMillis;

    public static void reset() {
        serverTicks = serverTickNanos = kineticTicks = kineticNanos = beltInvTicks = beltInvSkipped = 0;
        tickStart = 0;
        startedAtMillis = System.currentTimeMillis();
    }

    public static void beStart() {
        beStart = System.nanoTime();
    }

    public static void beEnd() {
        kineticNanos += System.nanoTime() - beStart;
        kineticTicks++;
    }

    public static void serverTickStart() {
        if (active) tickStart = System.nanoTime();
    }

    public static void serverTickEnd() {
        if (active && tickStart != 0) {
            serverTickNanos += System.nanoTime() - tickStart;
            serverTicks++;
        }
        tickStart = 0;
    }

    public static String report() {
        long t = Math.max(1, serverTicks);
        return String.format(
                "profile %s: %d server ticks (%.1fs wall) | avg server tick %.3f ms | Create kinetic BE ticks/tick %.1f, %.3f ms/tick (%.0f ns each) | belt inventory ticks/tick %.2f, skipped by jam backoff/tick %.2f",
                active ? "RUNNING" : "stopped", serverTicks, (System.currentTimeMillis() - startedAtMillis) / 1000.0,
                serverTickNanos / 1e6 / t, kineticTicks / (double) t, kineticNanos / 1e6 / t,
                kineticTicks == 0 ? 0.0 : kineticNanos / (double) kineticTicks, beltInvTicks / (double) t, beltInvSkipped / (double) t);
    }

    private KineticTickProfiler() {}
}
