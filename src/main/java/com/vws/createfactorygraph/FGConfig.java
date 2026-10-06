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

    private FGConfig() {}
}
