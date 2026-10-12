package com.vws.benchprobe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.piston.LinearActuatorBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Drives one headless dedicated-server run. Windows, in order: steady, churn (motor RPM
 * changes), steady2, cadence (synthetic CHEAP ship when the factory-graph mod is loaded).
 */
@Mod(BenchProbe.MOD_ID)
public class BenchProbe {
    public static final String MOD_ID = "benchprobe";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int SETTLE = Integer.getInteger("bench.settleTicks", 80);
    private static final int POWER_WAIT = Integer.getInteger("bench.powerTicks", 40);
    private static final int WARMUP = Integer.getInteger("bench.warmupTicks", 80);
    private static final int STEADY = Integer.getInteger("bench.steadyTicks", 300);
    private static final int CHURN = Integer.getInteger("bench.churnTicks", 120);
    private static final int STEADY2 = Integer.getInteger("bench.steady2Ticks", 150);
    private static final int CADENCE = Integer.getInteger("bench.cadenceTicks", 150);

    private final FactoryWorld world = new FactoryWorld();
    private Phase phase = Phase.BUILD;
    private int phaseTick;
    private boolean built;
    private ServerLevel level;

    private final List<Window> windows = new ArrayList<>();
    private Window current;
    private long tickStart;
    private boolean skipSample;

    public BenchProbe(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(this::onTickPre);
        NeoForge.EVENT_BUS.addListener(this::onTick);
    }

    private void onTickPre(ServerTickEvent.Pre event) {
        tickStart = System.nanoTime();
    }

    private void onTick(ServerTickEvent.Post event) {
        TickCounters.serverTicks++;
        MinecraftServer server = event.getServer();
        if (level == null) {
            level = server.overworld();
            prepare(server, level);
        }
        try {
            step(server);
        } catch (Throwable t) {
            LOGGER.error("benchprobe failed", t);
            writeError(t);
            server.halt(false);
            return;
        }
        if (skipSample) {
            skipSample = false;
            return;
        }
        if (current != null && tickStart != 0) current.add(System.nanoTime() - tickStart, level, world);
    }

    private void prepare(MinecraftServer server, ServerLevel level) {
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
        if (level.getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS) != null) {
            level.getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(10, server);
        }
    }

    private void step(MinecraftServer server) {
        switch (phase) {
            case BUILD -> {
                if (!built) {
                    world.build(level);
                    built = true;
                }
                if (++phaseTick >= 5) next(Phase.POWER);
            }
            case POWER -> {
                if (phaseTick == 0) world.power(level, FactoryWorld.RPM);
                if (++phaseTick >= POWER_WAIT) next(Phase.ASSEMBLE);
            }
            case ASSEMBLE -> {
                if (phaseTick == 0) world.assemble(level);
                if (++phaseTick >= SETTLE) {
                    world.sampleSpeeds(level);
                    LOGGER.info("benchprobe world {}", world.summary());
                    next(Phase.WARMUP);
                }
            }
            case WARMUP -> {
                if (++phaseTick >= WARMUP) beginWindow("steady", Phase.STEADY, STEADY);
            }
            case STEADY -> recordUntil(server, "churn", Phase.CHURN, CHURN, () -> world.churn(level));
            case CHURN -> recordUntil(server, "steady2", Phase.STEADY2, STEADY2, null);
            case STEADY2 -> recordUntil(server, "cadence", Phase.CADENCE, CADENCE, () -> startCadence(server));
            case CADENCE -> {
                if (current != null && current.ticks >= CADENCE) finish(server);
            }
            case DONE -> {
            }
        }
    }

    private void recordUntil(MinecraftServer server, String nextName, Phase nextPhase, int nextLen, Runnable onEnter) {
        if (current != null && current.ticks >= current.limit) {
            if (onEnter != null) onEnter.run();
            beginWindow(nextName, nextPhase, nextLen);
        }
    }

    private void beginWindow(String name, Phase next, int limit) {
        if (current != null) {
            current.closeCounters();
            windows.add(current);
        }
        current = new Window(name, limit);
        skipSample = true;
        phase = next;
        phaseTick = 0;
        LOGGER.info("benchprobe window {} ({} ticks)", name, limit);
    }

    private void next(Phase next) {
        phase = next;
        phaseTick = 0;
    }

    private void startCadence(MinecraftServer server) {
        if (!ModList.get().isLoaded("create_factory_graph")) {
            LOGGER.info("benchprobe cadence skipped (factory graph not loaded)");
            return;
        }
        var source = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, "factorygraph cadence policy slow");
        for (BlockPos pos : world.bearingMotors) parent(server, pos, "benchship");
        for (BlockPos pos : world.beltMotors) parent(server, pos, "benchship");
        for (BlockPos pos : world.lineMotors) parent(server, pos, "benchship");
        server.getCommands().performPrefixedCommand(source, "factorygraph cadence set benchship CHEAP 2");
        LOGGER.info("benchprobe synthetic CHEAP cadence on benchship period 2");
    }

    private void parent(MinecraftServer server, BlockPos pos, String key) {
        String cmd = "factorygraph cadence parent " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + key;
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4).withSuppressedOutput(), cmd);
    }

    private void finish(MinecraftServer server) {
        if (current != null) {
            current.closeCounters();
            windows.add(current);
            current = null;
        }
        phase = Phase.DONE;
        String json = render(server);
        Path out = Path.of(System.getProperty("bench.out", "bench-metrics.json"));
        try {
            Files.createDirectories(out.toAbsolutePath().getParent() == null ? Path.of(".") : out.toAbsolutePath().getParent());
            Files.writeString(out, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("benchprobe could not write {}", out, e);
        }
        LOGGER.info("BENCHPROBE_DONE {}", out.toAbsolutePath());
        server.halt(false);
    }

    private void writeError(Throwable t) {
        try {
            Path out = Path.of(System.getProperty("bench.out", "bench-metrics.json"));
            Files.writeString(out, "{\"error\":\"" + json(t.toString()) + "\"}\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private String render(MinecraftServer server) {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("{\n");
        sb.append("  \"label\": \"").append(json(System.getProperty("bench.label", "run"))).append("\",\n");
        sb.append("  \"pack\": {\"neoforge\": \"21.1.233\", \"minecraft\": \"1.21.1\", \"create\": \"6.0.10\", \"sable\": \"2.0.3\", \"aeronautics\": \"1.3.0\", \"sodium\": false},\n");
        sb.append("  \"modLoaded\": ").append(ModList.get().isLoaded("create_factory_graph")).append(",\n");
        sb.append("  \"skipContraptionTicks\": ").append(Pause.PERIOD).append(",\n");
        sb.append("  \"world\": \"").append(json(world.summary())).append("\",\n");
        sb.append("  \"modStats\": ").append(modStats()).append(",\n");
        sb.append("  \"windows\": {\n");
        for (int i = 0; i < windows.size(); i++) {
            if (i > 0) sb.append(",\n");
            sb.append(windows.get(i).json());
        }
        sb.append("\n  }\n}\n");
        return sb.toString();
    }

    private static String modStats() {
        if (!ModList.get().isLoaded("create_factory_graph")) return "null";
        try {
            Class<?> mgr = Class.forName("com.vws.createfactorygraph.graph.FactoryGraphManager");
            Object all = mgr.getMethod("all").invoke(null);
            long sleeps = 0, wakes = 0, rebuilds = 0, skipped = 0, beltSkips = 0, validations = 0, parented = 0, ground = 0;
            long buildNanos = 0;
            int graphs = 0, nodes = 0, dormant = 0;
            for (Object m : (Iterable<?>) all) {
                sleeps += field(m, "sleeps");
                wakes += field(m, "wakes");
                rebuilds += field(m, "rebuilds");
                skipped += field(m, "skippedBeTicks");
                beltSkips += field(m, "beltBackoffSkips");
                validations += field(m, "validations");
                parented += field(m, "parentedRebuilds");
                ground += field(m, "groundRebuilds");
                dormant += (int) m.getClass().getMethod("dormantCount").invoke(m);
                Object map = m.getClass().getField("graphs").get(m);
                if (map instanceof java.util.Map<?, ?> g) {
                    graphs += g.size();
                    for (Object graph : g.values()) {
                        nodes += ((java.util.Map<?, ?>) graph.getClass().getField("nodes").get(graph)).size();
                        buildNanos += (long) graph.getClass().getField("buildNanos").get(graph);
                    }
                }
            }
            Class<?> safety = Class.forName("com.vws.createfactorygraph.safety.Safety");
            long failOpen = (long) safety.getMethod("failOpens").invoke(null);
            return String.format(Locale.ROOT,
                    "{\"graphs\": %d, \"nodes\": %d, \"dormant\": %d, \"sleeps\": %d, \"wakes\": %d, \"rebuilds\": %d, \"skippedBeTicks\": %d, \"beltBackoffSkips\": %d, \"validations\": %d, \"parentedRebuilds\": %d, \"groundRebuilds\": %d, \"lastBuildNanosSum\": %d, \"failOpens\": %d}",
                    graphs, nodes, dormant, sleeps, wakes, rebuilds, skipped, beltSkips, validations, parented, ground, buildNanos, failOpen);
        } catch (Throwable t) {
            return "{\"error\": \"" + json(t.toString()) + "\"}";
        }
    }

    private static long field(Object o, String name) throws Exception {
        return o.getClass().getField(name).getLong(o);
    }

    private static String json(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private enum Phase { BUILD, POWER, ASSEMBLE, WARMUP, STEADY, CHURN, STEADY2, CADENCE, DONE }

    private static final class Window {
        final String name;
        final int limit;
        int ticks;
        final long[] nanos;
        final long kineticAtStart;
        final long beltAtStart;
        final long[] sectionNanos0;
        final long[] sectionCalls0;
        long kineticEnd;
        long beltEnd;
        long[] sectionNanos1;
        long[] sectionCalls1;
        final List<BearingTrace> bearings = new ArrayList<>();
        final List<PistonTrace> pistons = new ArrayList<>();
        final List<BeltTrace> belts = new ArrayList<>();
        boolean captured;

        Window(String name, int limit) {
            this.name = name;
            this.limit = limit;
            this.nanos = new long[limit];
            this.kineticAtStart = TickCounters.kineticTicks;
            this.beltAtStart = TickCounters.beltTicks;
            this.sectionNanos0 = Sections.nanos();
            this.sectionCalls0 = Sections.calls();
            this.kineticEnd = kineticAtStart;
            this.beltEnd = beltAtStart;
            this.sectionNanos1 = sectionNanos0;
            this.sectionCalls1 = sectionCalls0;
        }

        void closeCounters() {
            kineticEnd = TickCounters.kineticTicks;
            beltEnd = TickCounters.beltTicks;
            sectionNanos1 = Sections.nanos();
            sectionCalls1 = Sections.calls();
            LOGGER.info("benchprobe sections {} {}", name, sectionLine());
        }

        String sectionLine() {
            return Sections.line(sectionNanos0, sectionCalls0, sectionNanos1, sectionCalls1, ticks);
        }

        void add(long nano, ServerLevel level, FactoryWorld world) {
            if (ticks >= limit) return;
            nanos[ticks] = nano;
            capture(level, world, ticks);
            ticks++;
        }

        void capture(ServerLevel level, FactoryWorld world, int index) {
            if (!captured) {
                captured = true;
                var box = world.footprint().inflate(32);
                for (ControlledContraptionEntity entity : level.getEntitiesOfClass(ControlledContraptionEntity.class, box)) {
                    BlockPos anchor = BlockPos.containing(entity.getAnchorVec());
                    var be = level.getBlockEntity(anchor.below());
                    if (be instanceof MechanicalBearingBlockEntity) bearings.add(new BearingTrace(entity, limit));
                }
                for (BlockPos pos : world.pistons) {
                    if (level.getBlockEntity(pos) instanceof LinearActuatorBlockEntity actuator && actuator.movedContraption instanceof ControlledContraptionEntity entity) {
                        pistons.add(new PistonTrace(entity, actuator, limit));
                        LOGGER.info("benchprobe piston {} offset {} running {}", pos, actuator.offset, actuator.running);
                    } else {
                        LOGGER.warn("benchprobe piston {} has no contraption", pos);
                    }
                }
                for (BlockPos pos : world.beltStarts) {
                    if (level.getBlockEntity(pos) instanceof BeltBlockEntity belt) belts.add(new BeltTrace(belt, limit));
                }
                for (BlockPos pos : world.jammedStarts) {
                    if (level.getBlockEntity(pos) instanceof BeltBlockEntity belt) belts.add(new BeltTrace(belt, limit));
                }
                LOGGER.info("benchprobe {} tracking bearings={} pistons={} belts={}", name, bearings.size(), pistons.size(), belts.size());
            }
            for (BearingTrace b : bearings) b.sample(index);
            for (PistonTrace p : pistons) p.sample(index);
            for (BeltTrace b : belts) b.sample(index);
        }

        String json() {
            MotionMath.TickStats tick = MotionMath.TickStats.of(nanos, 0, ticks);
            MotionMath.Score bearing = MotionMath.Score.empty();
            for (BearingTrace b : bearings) bearing = MotionMath.pool(bearing, b.score());
            MotionMath.Score piston = MotionMath.Score.empty();
            for (PistonTrace p : pistons) piston = MotionMath.pool(piston, p.score());
            double belt = 0;
            int beltN = 0;
            for (BeltTrace b : belts) {
                belt += b.desync();
                beltN++;
            }
            if (beltN > 0) belt /= beltN;
            double kin = ticks == 0 ? 0 : (kineticEnd - kineticAtStart) / (double) ticks;
            double beltTicks = ticks == 0 ? 0 : (beltEnd - beltAtStart) / (double) ticks;
            return String.format(Locale.ROOT,
                    "    \"%s\": {\"ticks\": %d, \"msptMean\": %.4f, \"msptP99\": %.4f, \"msptStddev\": %.4f, \"kineticTicksPerTick\": %.3f, \"beltTicksPerTick\": %.3f, \"bearings\": %s, \"pistons\": %s, \"beltDesyncRms\": %.6f, \"sections\": %s}",
                    name, ticks, tick.mean, tick.p99, tick.stddev, kin, beltTicks, scoreJson(bearing, bearings.size()), scoreJson(piston, pistons.size()), belt,
                    Sections.json(sectionNanos0, sectionCalls0, sectionNanos1, sectionCalls1, ticks));
        }

        private static String scoreJson(MotionMath.Score s, int actors) {
            return String.format(Locale.ROOT,
                    "{\"actors\": %d, \"samples\": %d, \"desyncRms\": %.6f, \"jitterRms\": %.6f, \"jitterExcessRms\": %.6f, \"serverJitterRms\": %.6f, \"stepStddev\": %.6f, \"travel\": %.4f}",
                    actors, s.samples, s.desyncRms, s.jitterRms, s.jitterExcessRms, s.serverJitterRms, s.angleStepStddev, s.travel);
        }
    }

    private static final class BearingTrace {
        final ControlledContraptionEntity entity;
        final MechanicalBearingBlockEntity bearing;
        final double pivotX, pivotZ;
        final float[] angle, deg;
        final double[] x, y, z;
        final int cap;

        BearingTrace(ControlledContraptionEntity entity, int cap) {
            this.entity = entity;
            this.cap = cap;
            BlockPos anchor = BlockPos.containing(entity.getAnchorVec());
            MechanicalBearingBlockEntity found = null;
            for (int dy = -4; dy <= 2 && found == null; dy++) {
                if (entity.level().getBlockEntity(anchor.offset(0, dy, 0)) instanceof MechanicalBearingBlockEntity b) found = b;
            }
            this.bearing = found;
            Vec3 p = entity.position();
            this.pivotX = p.x;
            this.pivotZ = p.z;
            this.angle = new float[cap];
            this.deg = new float[cap];
            this.x = new double[cap];
            this.y = new double[cap];
            this.z = new double[cap];
        }

        void sample(int i) {
            if (i >= cap) return;
            float a = entity.getAngle(1f);
            angle[i] = a;
            deg[i] = bearing == null ? 0f : bearing.getAngularSpeed();
            Vec3 world = entity.toGlobalVector(new Vec3(1, 0, 1), 1f);
            x[i] = world.x;
            y[i] = world.y;
            z[i] = world.z;
        }

        MotionMath.Score score() {
            int n = cap;
            MotionMath.Score s = MotionMath.bearing(trim(angle, n), trim(deg, n), trim(x, n), trim(y, n), trim(z, n), pivotX, pivotZ);
            s.travel = path(angle, n, true);
            return s;
        }
    }

    private static final class PistonTrace {
        final ControlledContraptionEntity entity;
        final LinearActuatorBlockEntity actuator;
        final Direction.Axis axis;
        final float[] offset, rpm;
        final double[] x, y, z;
        final int cap;

        PistonTrace(ControlledContraptionEntity entity, LinearActuatorBlockEntity actuator, int cap) {
            this.entity = entity;
            this.cap = cap;
            this.actuator = actuator;
            Direction.Axis ax = Direction.Axis.Y;
            if (actuator.getBlockState().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
                ax = actuator.getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING).getAxis();
            }
            this.axis = ax;
            this.offset = new float[cap];
            this.rpm = new float[cap];
            this.x = new double[cap];
            this.y = new double[cap];
            this.z = new double[cap];
        }

        void sample(int i) {
            if (i >= cap) return;
            offset[i] = actuator == null ? 0f : actuator.offset;
            rpm[i] = actuator == null ? 0f : pistonLinear(actuator);
            x[i] = entity.getX();
            y[i] = entity.getY();
            z[i] = entity.getZ();
        }

        MotionMath.Score score() {
            double fx = axis == Direction.Axis.X ? 1 : 0;
            double fy = axis == Direction.Axis.Y ? 1 : 0;
            double fz = axis == Direction.Axis.Z ? 1 : 0;
            MotionMath.Score s = MotionMath.piston(trim(offset, cap), trim(rpm, cap), trim(x, cap), trim(y, cap), trim(z, cap), fx, fy, fz);
            s.travel = path(offset, cap, false);
            return s;
        }

        /** Blocks per tick, matching MechanicalPistonBlockEntity.getMovementSpeed without the client diff. */
        private static float pistonLinear(LinearActuatorBlockEntity actuator) {
            float blocks = Math.max(-0.49f, Math.min(0.49f, actuator.getSpeed() / 512f));
            if (!actuator.getBlockState().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
                return blocks;
            }
            Direction facing = actuator.getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
            int modifier = facing.getAxisDirection().getStep() * (facing.getAxis() == Direction.Axis.Z ? -1 : 1);
            return -blocks * modifier;
        }
    }

    private static final class BeltTrace {
        final BeltBlockEntity belt;
        final float[] pos, movement;
        final int cap;

        BeltTrace(BeltBlockEntity belt, int cap) {
            this.belt = belt;
            this.cap = cap;
            this.pos = new float[cap];
            this.movement = new float[cap];
        }

        void sample(int i) {
            if (i >= cap) return;
            float p = Float.NaN;
            var inv = belt.getInventory();
            if (inv != null) {
                for (var item : inv.getTransportedItems()) {
                    if (Float.isNaN(p) || item.beltPosition < p) p = item.beltPosition;
                }
            }
            pos[i] = Float.isNaN(p) ? 0f : p;
            movement[i] = Math.abs(belt.getBeltMovementSpeed());
        }

        double desync() {
            return MotionMath.beltDesyncRms(trim(pos, cap), trim(movement, cap));
        }
    }

    /** Sum of absolute steps. Bearings wrap at 360; piston offsets do not. */
    private static double path(float[] samples, int n, boolean wrap) {
        int m = Math.min(n, samples.length);
        if (m < 2) return 0;
        double sum = 0;
        for (int i = 1; i < m; i++) {
            float d = samples[i] - samples[i - 1];
            if (wrap) d = MotionMath.shortest(samples[i - 1], samples[i]);
            sum += Math.abs(d);
        }
        return sum;
    }

    private static float[] trim(float[] a, int n) {
        if (a.length == n) return a;
        float[] c = new float[n];
        System.arraycopy(a, 0, c, 0, Math.min(a.length, n));
        return c;
    }

    private static double[] trim(double[] a, int n) {
        if (a.length == n) return a;
        double[] c = new double[n];
        System.arraycopy(a, 0, c, 0, Math.min(a.length, n));
        return c;
    }
}
