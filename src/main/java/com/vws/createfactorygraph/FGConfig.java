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

    public static final ModConfigSpec.BooleanValue NEIGHBOUR_CACHE;
    public static final ModConfigSpec.BooleanValue STRESS_CACHE;
    public static final ModConfigSpec.BooleanValue LOGISTICS_CACHE;
    public static final ModConfigSpec.BooleanValue RECIPE_CACHE;
    public static final ModConfigSpec.BooleanValue TRAIN_POSITION_CACHE;
    public static final ModConfigSpec.BooleanValue CONTRAPTION_SHAPE_CACHE;

    public static final ModConfigSpec.BooleanValue KILL_SWITCH;
    public static final ModConfigSpec.IntValue MAX_CATCH_UP_PER_TICK;
    public static final ModConfigSpec.IntValue MAX_PARENTED_MEMBERS;

    /** What a ship-parented factory does while its ship is CHEAP. */
    public enum CheapPolicy { SLOW, SLEEP, BURST }

    /** Runtime overrides from /factorygraph cadence ... (null = use config). */
    public static volatile CheapPolicy runtimePolicy = null;
    public static volatile Boolean runtimeShipCadence = null;

    /** Runtime override from /factorygraph enable|disable (null = use config). */
    public static volatile Boolean runtimeEnabled = null;

    public static volatile Boolean runtimeNeighbourCache = null;
    public static volatile Boolean runtimeStressCache = null;
    public static volatile Boolean runtimeLogisticsCache = null;
    public static volatile Boolean runtimeRecipeCache = null;
    public static volatile Boolean runtimeTrainPositionCache = null;
    public static volatile Boolean runtimeContraptionShapeCache = null;

    /** Runtime kill switch from /factorygraph kill|unkill. Null follows the config file. */
    public static volatile Boolean runtimeKillSwitch = null;

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
        b.push("caches");
        NEIGHBOUR_CACHE = b.comment("Remember kinetic neighbour lists between block changes. The list is copied; Create's own list is left alone.")
                .define("neighbours", true);
        STRESS_CACHE = b.comment("Keep network stress and capacity incrementally. A cold network still runs Create's full scan. Removing a member drops that network's total. A chunk unload does not rescan every network on the server.")
                .define("stress", true);
        LOGISTICS_CACHE = b.comment("Remember funnel belt-segment lookups, depot behaviour lookups, and chute insert checks until a block changes. Chute results also key off the sign of item motion. Entity search stays on Create's 5-tick cadence.")
                .define("logistics", true);
        RECIPE_CACHE = b.comment("Remember mechanical-crafter recipe searches for an identical grid. Cleared when the recipe manager reloads. The stored stack is a copy.")
                .define("recipe", true);
        TRAIN_POSITION_CACHE = b.comment("Reuse TravellingPoint.getPositionWithOffset while the edge, graph, position, offset, and flip are unchanged. Signal scouting and collision still run.")
                .define("trainPosition", true);
        CONTRAPTION_SHAPE_CACHE = b.comment("Replay contraption block collision shapes for an unchanged local scan. Carriage contraptions that override portal hiding stay uncached. Actor ticks are unchanged.")
                .define("contraptionShapes", true);
        b.pop();

        b.push("safety");
        KILL_SWITCH = b.comment("Fail open. When true the graph, ship cadence, belt backoff, and every cache are inert. Create ticks as stock. In-memory debt is dropped and not replayed. /factorygraph kill does the same until restart.")
                .define("killSwitch", false);
        MAX_CATCH_UP_PER_TICK = b.comment("Server-wide cap on extra decorative-relay ticks repaid in one server tick. Machines, belts, and contraptions are never repaid.")
                .defineInRange("maxCatchUpPerTick", 64, 0, 100000);
        MAX_PARENTED_MEMBERS = b.comment("Kinetic networks larger than this are not parented to a ship. They tick at Create's rate. Stops a partial ship-group freeze from scanning a megabase every rebuild.")
                .defineInRange("maxParentedMembers", 8192, 16, 1000000);
        b.pop();
        SPEC = b.build();
    }

    /**
     * True when {@code /factorygraph kill} is on, or the config kill switch is on.
     * A runtime false does not turn a config kill off. {@code /factorygraph unkill} clears the runtime value.
     */
    public static boolean killed() {
        boolean configOn = SPEC.isLoaded() && KILL_SWITCH.get();
        return com.vws.createfactorygraph.safety.Safety.killActive(runtimeKillSwitch, configOn);
    }

    public static boolean enabled() {
        if (killed()) return false;
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
    public static boolean jamBackoff() { return !killed() && get(BELT_JAM_BACKOFF, true); }
    public static int jamDetectTicks() { return get(JAM_DETECT_TICKS, 40); }
    public static int maxBackoffTicks() { return get(MAX_BACKOFF_TICKS, 8); }
    public static int maxRebuildsPerTick() { return get(MAX_REBUILDS_PER_TICK, 16); }
    public static int maxEdgeNodes() { return get(MAX_EDGE_NODES, 10000); }

    public static boolean shipCadence() {
        if (killed()) return false;
        Boolean rt = runtimeShipCadence;
        return rt != null ? rt : get(SHIP_CADENCE, true);
    }

    public static int maxCatchUpPerTick() { return get(MAX_CATCH_UP_PER_TICK, 64); }
    public static int maxParentedMembers() { return get(MAX_PARENTED_MEMBERS, 8192); }
    public static CheapPolicy cheapPolicy() {
        CheapPolicy rt = runtimePolicy;
        return rt != null ? rt : get(CHEAP_POLICY, CheapPolicy.SLOW);
    }
    public static int defaultHullPeriod() { return get(DEFAULT_HULL_PERIOD, 2); }
    public static int catchUpTicksPerTick() { return get(CATCH_UP_PER_TICK, 2); }
    public static int maxDebtTicks() { return get(MAX_DEBT_TICKS, 6000); }
    public static int cheapSettleTicks() { return get(CHEAP_SETTLE_TICKS, 20); }


    private static boolean flag(Boolean runtime, ModConfigSpec.BooleanValue value, boolean def) {
        if (killed()) return false;
        return runtime != null ? runtime : get(value, def);
    }

    public static boolean neighbourCache() { return flag(runtimeNeighbourCache, NEIGHBOUR_CACHE, true); }
    public static boolean stressCache() { return flag(runtimeStressCache, STRESS_CACHE, true); }
    public static boolean logisticsCache() { return flag(runtimeLogisticsCache, LOGISTICS_CACHE, true); }
    public static boolean recipeCache() { return flag(runtimeRecipeCache, RECIPE_CACHE, true); }
    public static boolean trainPositionCache() { return flag(runtimeTrainPositionCache, TRAIN_POSITION_CACHE, true); }
    public static boolean contraptionShapeCache() { return flag(runtimeContraptionShapeCache, CONTRAPTION_SHAPE_CACHE, true); }

    private FGConfig() {}
}
