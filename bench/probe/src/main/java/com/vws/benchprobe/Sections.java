package com.vws.benchprobe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Inclusive wall time for the Create and vanilla phases that can dominate a tick.
 * Server thread only. Nested sections are reported separately, not subtracted.
 */
public final class Sections {
    /** Off unless -Dbench.sections=true. A static final lets the JIT drop the probes. */
    public static final boolean ENABLED = Boolean.getBoolean("bench.sections");

    private static final List<Acc> ALL = new ArrayList<>();

    public static final Acc blockEntities = acc("blockEntities");
    public static final Acc entityTick = acc("entityTick");
    public static final Acc contraptionEntity = acc("contraptionEntity");
    public static final Acc worldEvents = acc("worldEvents");
    public static final Acc serverEvents = acc("serverEvents");
    public static final Acc collide = acc("collide");
    public static final Acc railways = acc("railways");
    public static final Acc logistics = acc("logistics");
    public static final Acc kinetic = acc("kinetic");
    public static final Acc belt = acc("belt");
    public static final Acc beltFunnel = acc("beltFunnel");
    public static final Acc fan = acc("fan");
    public static final Acc fanEntities = acc("fanEntities");
    public static final Acc deployer = acc("deployer");
    public static final Acc press = acc("press");
    public static final Acc mixer = acc("mixer");
    public static final Acc funnel = acc("funnel");
    public static final Acc depot = acc("depot");
    public static final Acc chute = acc("chute");
    public static final Acc fluidTransport = acc("fluidTransport");
    public static final Acc fluidNetwork = acc("fluidNetwork");
    public static final Acc shouldTick = acc("shouldTick");
    public static final Acc beTicker = acc("beTicker");
    public static final Acc bearing = acc("bearing");
    public static final Acc actuator = acc("actuator");

    private static Acc acc(String name) {
        Acc a = new Acc(name);
        ALL.add(a);
        return a;
    }

    public static long[] nanos() {
        long[] out = new long[ALL.size()];
        for (int i = 0; i < ALL.size(); i++) out[i] = ALL.get(i).nanos;
        return out;
    }

    public static long[] calls() {
        long[] out = new long[ALL.size()];
        for (int i = 0; i < ALL.size(); i++) out[i] = ALL.get(i).calls;
        return out;
    }

    public static String json(long[] nanos0, long[] calls0, long[] nanos1, long[] calls1, int ticks) {
        double t = Math.max(1, ticks);
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (int i = 0; i < ALL.size(); i++) {
            if (i > 0) sb.append(", ");
            long dn = nanos1[i] - nanos0[i];
            long dc = calls1[i] - calls0[i];
            sb.append('"').append(ALL.get(i).name).append("\": {");
            sb.append(String.format(Locale.ROOT, "\"msPerTick\": %.4f, \"callsPerTick\": %.2f", dn / 1e6 / t, dc / t));
            sb.append('}');
        }
        sb.append('}');
        return sb.toString();
    }

    public static String line(long[] nanos0, long[] calls0, long[] nanos1, long[] calls1, int ticks) {
        double t = Math.max(1, ticks);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ALL.size(); i++) {
            long dn = nanos1[i] - nanos0[i];
            long dc = calls1[i] - calls0[i];
            if (dc == 0 && dn == 0) continue;
            if (sb.length() > 0) sb.append(" | ");
            sb.append(String.format(Locale.ROOT, "%s %.3f ms x%.1f", ALL.get(i).name, dn / 1e6 / t, dc / t));
        }
        return sb.toString();
    }

    public static final class Acc {
        public final String name;
        public long nanos;
        public long calls;
        private long open;
        private int depth;

        Acc(String name) {
            this.name = name;
        }

        public void begin() {
            if (!ENABLED) return;
            if (depth++ == 0) open = System.nanoTime();
        }

        public void end() {
            if (!ENABLED || depth <= 0) return;
            if (--depth == 0) {
                nanos += System.nanoTime() - open;
                calls++;
            }
        }
    }

    private Sections() {}
}
