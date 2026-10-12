package com.vws.benchprobe;

/** Probe-only experiment. Default off. Not part of create_factory_graph. */
public final class Pause {
    /** Skip contraption controller ticks unless the server tick is divisible by this. 0 = off. */
    public static final int PERIOD = Integer.getInteger("bench.skipContraptionTicks", 0);

    public static boolean skip() {
        if (PERIOD <= 1) return false;
        return (TickCounters.serverTicks % PERIOD) != 0;
    }

    private Pause() {}
}
