package com.vws.createfactorygraph.profile;

/**
 * Opt-in per-area wall time. Counters stay at zero unless {@link #active} is set.
 * Server thread only.
 */
public final class AreaProfiler {
    public static volatile boolean active;

    public static final Section neighbourScan = new Section();
    public static final Section stressCalc = new Section();
    public static final Section funnel = new Section();
    public static final Section depot = new Section();
    public static final Section chute = new Section();
    public static final Section trainPosition = new Section();
    public static final Section contraptionShapes = new Section();
    public static final Section contraptionCollide = new Section();

    public static long neighbourHits, neighbourMisses;
    public static long stressHits, stressMisses;
    public static long logisticsHits, logisticsMisses;
    public static long recipeHits, recipeMisses;
    public static long trainPosHits, trainPosMisses;
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
        for (Section s : new Section[] {neighbourScan, stressCalc, funnel, depot, chute, trainPosition, contraptionShapes, contraptionCollide}) {
            s.nanos = 0;
            s.calls = 0;
            s.start = 0;
        }
        neighbourHits = neighbourMisses = 0;
        stressHits = stressMisses = 0;
        logisticsHits = logisticsMisses = 0;
        recipeHits = recipeMisses = 0;
        trainPosHits = trainPosMisses = 0;
        shapeHits = shapeMisses = 0;
    }

    public static String line(String name, Section s, long ticks) {
        double t = Math.max(1, ticks);
        return String.format("%s: %.3f ms/tick (%d calls, %.0f ns/call)", name, s.nanos / 1e6 / t, s.calls, s.calls == 0 ? 0 : s.nanos / (double) s.calls);
    }

    public static String report(long ticks) {
        StringBuilder sb = new StringBuilder();
        sb.append(line("neighbourScan", neighbourScan, ticks)).append('\n');
        sb.append(line("stressCalc", stressCalc, ticks)).append('\n');
        sb.append(line("funnel", funnel, ticks)).append('\n');
        sb.append(line("depot", depot, ticks)).append('\n');
        sb.append(line("chute", chute, ticks)).append('\n');
        sb.append(line("trainPosition", trainPosition, ticks)).append('\n');
        sb.append(line("contraptionShapes", contraptionShapes, ticks)).append('\n');
        sb.append(line("contraptionCollide", contraptionCollide, ticks)).append('\n');
        sb.append(String.format(
                "hits neighbour %d/%d stress %d/%d logistics %d/%d recipe %d/%d train %d/%d shape %d/%d",
                neighbourHits, neighbourHits + neighbourMisses,
                stressHits, stressHits + stressMisses,
                logisticsHits, logisticsHits + logisticsMisses,
                recipeHits, recipeHits + recipeMisses,
                trainPosHits, trainPosHits + trainPosMisses,
                shapeHits, shapeHits + shapeMisses));
        return sb.toString();
    }

    private AreaProfiler() {}
}
