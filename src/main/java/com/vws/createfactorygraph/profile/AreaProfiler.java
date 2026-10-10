package com.vws.createfactorygraph.profile;

/**
 * Opt-in per-area wall time. Counters stay at zero unless {@link #active} is set.
 * Server thread only.
 */
public final class AreaProfiler {
    public static volatile boolean active;

    public static final Section funnel = new Section();
    public static final Section depot = new Section();
    public static final Section chute = new Section();

    public static long logisticsHits, logisticsMisses;

    public static final class Section {
        public long nanos;
        public long calls;
        private long start;

        public void begin() {
            if (active) start = System.nanoTime();
        }

        public void end() {
            if (active && start != 0) {
                nanos += System.nanoTime() - start;
                calls++;
            }
            start = 0;
        }
    }

    public static void reset() {
        for (Section s : new Section[] {funnel, depot, chute}) {
            s.nanos = 0;
            s.calls = 0;
            s.start = 0;
        }
        logisticsHits = logisticsMisses = 0;
    }

    public static String line(String name, Section s, long ticks) {
        double t = Math.max(1, ticks);
        return String.format("%s: %.3f ms/tick (%d calls, %.0f ns/call)", name, s.nanos / 1e6 / t, s.calls, s.calls == 0 ? 0 : s.nanos / (double) s.calls);
    }

    public static String report(long ticks) {
        StringBuilder sb = new StringBuilder();
        sb.append(line("funnel", funnel, ticks)).append('\n');
        sb.append(line("depot", depot, ticks)).append('\n');
        sb.append(line("chute", chute, ticks)).append('\n');
        sb.append(String.format(
                "hits logistics %d/%d",
                logisticsHits, logisticsHits + logisticsMisses));
        return sb.toString();
    }

    private AreaProfiler() {}
}
