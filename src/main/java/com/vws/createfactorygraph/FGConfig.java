package com.vws.createfactorygraph;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Common config: config/create_factory_graph-common.toml */
public final class FGConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.BooleanValue DORMANT_RELAYS;
    public static final ModConfigSpec.BooleanValue DORMANT_BELT_SEGMENTS;
    public static final ModConfigSpec.IntValue SETTLE_TICKS;
    public static final ModConfigSpec.BooleanValue DORMANT_VALIDATION;
    public static final ModConfigSpec.BooleanValue BELT_JAM_BACKOFF;
    public static final ModConfigSpec.IntValue JAM_DETECT_TICKS;
    public static final ModConfigSpec.IntValue MAX_BACKOFF_TICKS;
    public static final ModConfigSpec.IntValue MAX_REBUILDS_PER_TICK;
    public static final ModConfigSpec.IntValue MAX_EDGE_NODES;
    public static final ModConfigSpec.BooleanValue SHIP_CADENCE;
    public static final ModConfigSpec.EnumValue<CheapPolicy> CHEAP_POLICY;
    public static final ModConfigSpec.IntValue DEFAULT_HULL_PERIOD;
    public static final ModConfigSpec.IntValue CATCH_UP_PER_TICK;
    public static final ModConfigSpec.IntValue MAX_DEBT_TICKS;
    public static final ModConfigSpec.IntValue CHEAP_SETTLE_TICKS;

    /** What a ship-parented factory does while its ship is CHEAP. */
    public enum CheapPolicy { SLOW, SLEEP, BURST }

    /** Runtime overrides from /factorygraph cadence ... (null = use config). */
    public static volatile CheapPolicy runtimePolicy = null;
    public static volatile Boolean runtimeShipCadence = null;

    /** Runtime override from /factorygraph enable|disable (null = use config). */
    public static volatile Boolean runtimeEnabled = null;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("graph");
        ENABLED = b.comment("Master switch. When false the mod is inert and Create ticks exactly as stock.")
                .define("enabled", true);
        MAX_REBUILDS_PER_TICK = b.comment("Max dirty kinetic networks rebuilt into graphs per level tick (rest deferred).")
                .defineInRange("maxRebuildsPerTick", 16, 1, 1024);
        MAX_EDGE_NODES = b.comment("Networks larger than this get nodes but no edge list (keeps rebuilds bounded).")
                .defineInRange("maxEdgeNodes", 10000, 100, 1000000);
        b.pop();

        b.push("dormancy");
        DORMANT_RELAYS = b.comment("Stop ticking settled decorative relays (shaft, cogwheel, large cogwheel, encased shaft/cog, gearbox, chain drive).")
                .define("dormantRelays", true);
        DORMANT_BELT_SEGMENTS = b.comment("Stop ticking settled non-controller belt segments (the controller still ticks the whole belt).")
                .define("dormantBeltSegments", true);
        SETTLE_TICKS = b.comment("Consecutive quiet ticks before a relay goes dormant.")
                .defineInRange("settleTicks", 40, 1, 12000);
        DORMANT_VALIDATION = b.comment("Run Create's periodic source validation for dormant nodes from the graph at Create's own kineticValidationFrequency (safety net).")
                .define("dormantValidation", true);
        b.pop();

        b.push("belts");
        BELT_JAM_BACKOFF = b.comment("Back off ticking a jammed belt's item inventory (no item movement at all). Resumes instantly on insert/change.")
                .define("jamBackoff", true);
        JAM_DETECT_TICKS = b.comment("Ticks with zero item movement before a belt counts as jammed.")
                .defineInRange("jamDetectTicks", 40, 5, 1200);
        MAX_BACKOFF_TICKS = b.comment("Max ticks between inventory ticks while jammed (= worst-case resume latency once the jam clears).")
                .defineInRange("maxBackoffTicks", 8, 1, 100);
        b.pop();

        b.push("ship_cadence");
        SHIP_CADENCE = b.comment("Phase 4: factory graphs on an Aeronautics ship follow the ship's hull cadence (needs aeronautics_ship_hull; ground factories are never affected).")
                .define("enabled", true);
        CHEAP_POLICY = b.comment("While the ship is CHEAP: SLOW = tick once per hull period and defer the rest; SLEEP = defer every tick; BURST = run the whole period back-to-back on the hull step (no saving, batching only). Deferred ticks are replayed after the ship wakes.")
                .defineEnum("cheapPolicy", CheapPolicy.SLOW);
        DEFAULT_HULL_PERIOD = b.comment("Hull period (ticks) used if the ship mod does not report one.")
                .defineInRange("defaultHullPeriod", 2, 1, 40);
        CATCH_UP_PER_TICK = b.comment("Max deferred ticks a block entity replays per server tick once its ship is awake (FULL/REBUILDING).")
                .defineInRange("catchUpTicksPerTick", 2, 1, 64);
        MAX_DEBT_TICKS = b.comment("Max deferred ticks remembered per block entity; beyond this the work is forfeited (items are never touched).")
                .defineInRange("maxDebtTicks", 6000, 0, 72000);
        CHEAP_SETTLE_TICKS = b.comment("Ship must stay CHEAP this many ticks before its factories slow (debounces mode flaps).")
                .defineInRange("cheapSettleTicks", 20, 0, 1200);
        b.pop();
        SPEC = b.build();
    }

    public static boolean enabled() {
        Boolean rt = runtimeEnabled;
        if (rt != null) return rt;
        return SPEC.isLoaded() ? ENABLED.get() : false;
    }

    private static <T> T get(ModConfigSpec.ConfigValue<T> v, T def) {
        return SPEC.isLoaded() ? v.get() : def;
    }

    public static boolean dormantRelays() { return get(DORMANT_RELAYS, true); }
    public static boolean dormantBeltSegments() { return get(DORMANT_BELT_SEGMENTS, true); }
    public static int settleTicks() { return get(SETTLE_TICKS, 40); }
    public static boolean dormantValidation() { return get(DORMANT_VALIDATION, true); }
    public static boolean jamBackoff() { return get(BELT_JAM_BACKOFF, true); }
    public static int jamDetectTicks() { return get(JAM_DETECT_TICKS, 40); }
    public static int maxBackoffTicks() { return get(MAX_BACKOFF_TICKS, 8); }
    public static int maxRebuildsPerTick() { return get(MAX_REBUILDS_PER_TICK, 16); }
    public static int maxEdgeNodes() { return get(MAX_EDGE_NODES, 10000); }

    public static boolean shipCadence() {
        Boolean rt = runtimeShipCadence;
        return rt != null ? rt : get(SHIP_CADENCE, true);
    }
    public static CheapPolicy cheapPolicy() {
        CheapPolicy rt = runtimePolicy;
        return rt != null ? rt : get(CHEAP_POLICY, CheapPolicy.SLOW);
    }
    public static int defaultHullPeriod() { return get(DEFAULT_HULL_PERIOD, 2); }
    public static int catchUpTicksPerTick() { return get(CATCH_UP_PER_TICK, 2); }
    public static int maxDebtTicks() { return get(MAX_DEBT_TICKS, 6000); }
    public static int cheapSettleTicks() { return get(CHEAP_SETTLE_TICKS, 20); }

    private FGConfig() {}
}
