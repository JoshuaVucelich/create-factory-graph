package com.vws.createfactorygraph.profile;

/**
 * Opt-in per-area wall time. Counters stay at zero unless {@link #active} is set.
 * Server thread only.
 */
public final class AreaProfiler {
    public static volatile boolean active;

    public static final Section contraptionShapes = new Section();
    public static final Section contraptionCollide = new Section();

    public static long shapeHits, shapeMisses;

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
        for (Section s : new Section[] {contraptionShapes, contraptionCollide}) {
            s.nanos = 0;
            s.calls = 0;
            s.start = 0;
        }
        shapeHits = shapeMisses = 0;
    }

    public static String line(String name, Section s, long ticks) {
        double t = Math.max(1, ticks);
        return String.format("%s: %.3f ms/tick (%d calls, %.0f ns/call)", name, s.nanos / 1e6 / t, s.calls, s.calls == 0 ? 0 : s.nanos / (double) s.calls);
    }

    public static String report(long ticks) {
        StringBuilder sb = new StringBuilder();
        sb.append(line("contraptionShapes", contraptionShapes, ticks)).append('\n');
        sb.append(line("contraptionCollide", contraptionCollide, ticks)).append('\n');
        sb.append(String.format(
                "hits shape %d/%d",
                shapeHits, shapeHits + shapeMisses));
        return sb.toString();
    }

    private AreaProfiler() {}
}
