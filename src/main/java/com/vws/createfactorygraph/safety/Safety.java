package com.vws.createfactorygraph.safety;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Pure decisions for the hardening pass. No Minecraft types, so the rules can be unit-tested
 * without a server. Cadence debt and cache contents are never written to disk
 * ({@link #PERSIST_CADENCE_OR_CACHE}).
 */
public final class Safety {
    /** Matches {@code DecorativePolicy.RELAY}. Only this kind may skip ticks. */
    public static final byte RELAY = 1;
    /** How many members of a network are asked which ship they stand on. */
    public static final int MAX_PARENT_SAMPLES = 16;
    /** Cadence debt, dormant flags, and caches are memory only. A crash drops them. */
    public static final boolean PERSIST_CADENCE_OR_CACHE = false;

    public enum Parenting { GROUND, PARENT, REFUSE }

    public static final class ParentChoice {
        public final Parenting kind;
        public final Object ship;

        private ParentChoice(Parenting kind, Object ship) {
            this.kind = kind;
            this.ship = ship;
        }

        public static ParentChoice ground() { return new ParentChoice(Parenting.GROUND, null); }
        public static ParentChoice refuse() { return new ParentChoice(Parenting.REFUSE, null); }
        public static ParentChoice parent(Object ship) { return new ParentChoice(Parenting.PARENT, ship); }
    }

    private static final AtomicLong FAIL_OPENS = new AtomicLong();
    private static final AtomicLong SPLIT_UNGATES = new AtomicLong();
    private static final AtomicLong FORFEITED_UNSAFE_DEBT = new AtomicLong();
    private static final AtomicLong PARENT_REFUSALS = new AtomicLong();

    public static long failOpens() { return FAIL_OPENS.get(); }
    public static long splitUngates() { return SPLIT_UNGATES.get(); }
    public static long forfeitedUnsafeDebt() { return FORFEITED_UNSAFE_DEBT.get(); }
    public static long parentRefusals() { return PARENT_REFUSALS.get(); }

    /**
     * Config kill stays on unless the runtime override is explicitly true-or-absent.
     * A runtime {@code false} must not mask {@code safety.killSwitch = true}.
     * {@code /factorygraph unkill} stores null so this method follows the config again.
     */
    public static boolean killActive(Boolean runtime, boolean configOn) {
        if (Boolean.TRUE.equals(runtime)) return true;
        return configOn;
    }

    /** True on the first failure and every 64th after that, so a hot hook cannot flood the log. */
    public static boolean noteFailOpen() {
        long n = FAIL_OPENS.incrementAndGet();
        return (n & 63L) == 1L;
    }

    public static void noteSplit() { SPLIT_UNGATES.incrementAndGet(); }
    public static void noteForfeit(int ticks) { if (ticks > 0) FORFEITED_UNSAFE_DEBT.addAndGet(ticks); }
    public static void noteRefusal() { PARENT_REFUSALS.incrementAndGet(); }

    public static void resetCounters() {
        FAIL_OPENS.set(0);
        SPLIT_UNGATES.set(0);
        FORFEITED_UNSAFE_DEBT.set(0);
        PARENT_REFUSALS.set(0);
    }

    /** True when both sides name the same ship. Null never matches a ship. */
    public static boolean sameKey(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /**
     * Parent only when every sample names the same ship and the network is small enough to re-check.
     * A ground block, a second ship, an empty sample, or a network over the cap refuses: those
     * block entities keep Create's own tick. Refusing is how a kinetic line that crosses a
     * partially frozen ship group stays on the flying part.
     */
    public static ParentChoice choose(Object[] samples, int members, int maxMembers) {
        if (members <= 0 || members > maxMembers || samples == null || samples.length == 0) return ParentChoice.refuse();
        Object ship = samples[0];
        for (int i = 1; i < samples.length; i++) {
            if (!sameKey(ship, samples[i])) return ParentChoice.refuse();
        }
        if (ship == null) return ParentChoice.ground();
        return ParentChoice.parent(ship);
    }

    /** Indices that cover both ends of the member list plus an even spread, capped at {@code maxSamples}. */
    public static int[] sampleIndices(int members, int maxSamples) {
        if (members <= 0) return new int[0];
        int n = Math.min(members, Math.max(1, maxSamples));
        if (n == members) {
            int[] all = new int[members];
            for (int i = 0; i < members; i++) all[i] = i;
            return all;
        }
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) idx[i] = (int) ((long) i * (members - 1) / (n - 1));
        int w = 1;
        for (int i = 1; i < n; i++) {
            if (idx[i] != idx[w - 1]) idx[w++] = idx[i];
        }
        if (w == n) return idx;
        return java.util.Arrays.copyOf(idx, w);
    }

    /** Decorative relays only. Belts, deployers, bearings, and every other machine return false. */
    public static boolean mayDefer(byte decorativeKind, boolean killed, boolean cadenceEnabled) {
        return !killed && cadenceEnabled && decorativeKind == RELAY;
    }

    /**
     * Extra {@code tick()} calls to run now. Anything that is not a relay returns 0: its debt is
     * dropped, not replayed, so a wake cannot move items twice or spin a contraption through
     * several ticks inside one server tick.
     */
    public static int replayTicks(byte decorativeKind, int debt, int budget) {
        if (decorativeKind != RELAY || debt <= 0 || budget <= 0) return 0;
        return Math.min(debt, budget);
    }

    /** A hook that threw must not cancel Create's own tick. */
    public static boolean skipOriginal(boolean wantSkip, boolean hookFailed) {
        return wantSkip && !hookFailed;
    }

    private Safety() {}
}
