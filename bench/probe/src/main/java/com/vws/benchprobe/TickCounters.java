package com.vws.benchprobe;

public final class TickCounters {
    public static long serverTicks;
    public static long kineticTicks;
    public static long beltTicks;

    public static void resetWindow() {
        kineticTicks = 0;
        beltTicks = 0;
    }

    private TickCounters() {}
}
