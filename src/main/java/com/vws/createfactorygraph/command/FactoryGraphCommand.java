package com.vws.createfactorygraph.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.vws.createfactorygraph.api.FactoryCadence;
import com.vws.createfactorygraph.cadence.CadenceDuck;
import com.vws.createfactorygraph.cadence.ShipGate;
import com.mojang.brigadier.context.CommandContext;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.vws.createfactorygraph.FGConfig;
import com.vws.createfactorygraph.graph.FactoryGraph;
import com.vws.createfactorygraph.graph.FactoryGraphManager;
import com.vws.createfactorygraph.graph.GraphNode;
import com.vws.createfactorygraph.hooks.KineticNodeDuck;
import com.vws.createfactorygraph.profile.KineticTickProfiler;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/** /factorygraph status|enable|disable|graphs|inspect <pos>|rebuild|profile start|stop|report (op level 2). */
public final class FactoryGraphCommand {

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("factorygraph")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("status").executes(FactoryGraphCommand::status))
                .then(Commands.literal("enable").executes(c -> setEnabled(c, true)))
                .then(Commands.literal("disable").executes(c -> setEnabled(c, false)))
                .then(Commands.literal("graphs").executes(FactoryGraphCommand::graphs))
                .then(Commands.literal("rebuild").executes(FactoryGraphCommand::rebuild))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(FactoryGraphCommand::inspect)))
                .then(Commands.literal("cadence")
                        .executes(FactoryGraphCommand::cadence)
                        .then(Commands.literal("on").executes(c -> { FGConfig.runtimeShipCadence = true; return say(c, "ship cadence ON"); }))
                        .then(Commands.literal("off").executes(c -> { FGConfig.runtimeShipCadence = false; return say(c, "ship cadence OFF (parented BEs repay debt and tick normally)"); }))
                        .then(Commands.literal("policy")
                                .then(Commands.argument("policy", StringArgumentType.word()).executes(FactoryGraphCommand::policy)))
                        .then(Commands.literal("parent")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .then(Commands.argument("key", StringArgumentType.string()).executes(FactoryGraphCommand::parent))))
                        .then(Commands.literal("unparent")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(FactoryGraphCommand::unparent)))
                        .then(Commands.literal("set")
                                .then(Commands.argument("key", StringArgumentType.string())
                                        .then(Commands.argument("mode", StringArgumentType.word())
                                                .executes(c -> setMode(c, -1))
                                                .then(Commands.argument("period", IntegerArgumentType.integer(1, 40))
                                                        .executes(c -> setMode(c, IntegerArgumentType.getInteger(c, "period")))))))
                        .then(Commands.literal("inspect")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(FactoryGraphCommand::cadenceInspect))))
                .then(Commands.literal("profile")
                        .then(Commands.literal("start").executes(c -> {
                            KineticTickProfiler.reset();
                            KineticTickProfiler.active = true;
                            return say(c, "factorygraph profiler started (graph mode " + (FGConfig.enabled() ? "ON" : "OFF") + ")");
                        }))
                        .then(Commands.literal("stop").executes(c -> {
                            KineticTickProfiler.active = false;
                            return say(c, KineticTickProfiler.report());
                        }))
                        .then(Commands.literal("report").executes(c -> say(c, KineticTickProfiler.report())))));
    }

    private static int say(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        say(c, "factorygraph: graph mode " + (FGConfig.enabled() ? "ENABLED" : "DISABLED")
                + (FGConfig.runtimeEnabled != null ? " (runtime override)" : ""));
        for (FactoryGraphManager m : FactoryGraphManager.all()) {
            say(c, String.format(
                    "[%s] graphs=%d nodes=%d edges=%d dirty=%d | dormant=%d sleeps=%d wakes=%d rebounds=%d | BE ticks skipped=%d validations=%d | rebuilds=%d advanceTicks=%d | belts jammed now=%d jamEvents=%d resumes=%d backoffSkips=%d",
                    m.level.dimension().location(), m.graphs.size(), m.nodeTotal(), m.edgeTotal(), m.pendingDirty(),
                    m.dormantCount(), m.sleeps, m.wakes, m.rebounds, m.skippedBeTicks, m.validations, m.rebuilds,
                    m.advanceTicks, m.jammedBelts.size(), m.beltJamEvents, m.beltResumes, m.beltBackoffSkips));
        }
        return 1;
    }

    private static int setEnabled(CommandContext<CommandSourceStack> c, boolean on) {
        FGConfig.runtimeEnabled = on;
        if (!on) {
            for (FactoryGraphManager m : FactoryGraphManager.all()) {
                m.wakeAll("disabled");
                m.graphs.clear();
            }
        } else {
            for (FactoryGraphManager m : FactoryGraphManager.all()) m.markAllKnownDirty();
        }
        return say(c, "factorygraph graph mode " + (on ? "ENABLED" : "DISABLED (all dormant relays woken, Create stock ticking)"));
    }

    private static int graphs(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        FactoryGraphManager m = FactoryGraphManager.get(level);
        List<FactoryGraph> list = new ArrayList<>(m.graphs.values());
        list.sort(Comparator.comparingInt((FactoryGraph g) -> g.nodes.size()).reversed());
        say(c, list.size() + " graphs in " + level.dimension().location());
        for (int i = 0; i < Math.min(10, list.size()); i++) {
            FactoryGraph g = list.get(i);
            long dormant = g.nodes.values().stream().filter(n -> n.dormant).count();
            say(c, " " + g.summary() + ", dormant(at build) " + dormant);
        }
        return list.size();
    }

    private static int rebuild(CommandContext<CommandSourceStack> c) {
        for (FactoryGraphManager m : FactoryGraphManager.all()) m.markAllKnownDirty();
        return say(c, "factorygraph: all known networks marked for rebuild");
    }

    private static int inspect(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = c.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        if (!(level.getBlockEntity(pos) instanceof KineticBlockEntity be)) {
            return say(c, "not a Create kinetic block entity at " + pos.toShortString());
        }
        KineticNodeDuck duck = (KineticNodeDuck) be;
        say(c, String.format("%s %s speed=%.1f network=%s dormant=%s quietTicks=%d candidate=%d",
                pos.toShortString(), FactoryGraph.classify(be), be.getSpeed(), be.network, duck.cfg$isDormant(),
                duck.cfg$quietTicks(), duck.cfg$candidate()));
        if (be.network != null) {
            FactoryGraph g = FactoryGraphManager.get(level).graphs.get(be.network.longValue());
            if (g == null) return say(c, " graph: not built yet (pending=" + FactoryGraphManager.get(level).pendingDirty() + ")");
            say(c, " graph " + g.summary());
            GraphNode n = g.nodes.get(pos.asLong());
            if (n != null) {
                int deg = 0;
                for (int i = 0; i < g.edges.size(); i += 2) if (g.edges.getLong(i) == pos.asLong() || g.edges.getLong(i + 1) == pos.asLong()) deg++;
                say(c, String.format(" node %s kind=%s impact=%.2f cap=%.1f items=%d degree=%d", n.blockId, n.kind, n.stressImpact, n.capacity, n.inventoryItems, deg));
            }
        }
        return 1;
    }

    static Object parseKey(String k) {
        try { return java.util.UUID.fromString(k); } catch (Exception e) { return k; }
    }

    private static int cadence(CommandContext<CommandSourceStack> c) {
        say(c, String.format("cadence: %s policy=%s catchUp/tick=%d maxDebt=%d locator=%s | events=%d (cheap=%d rebuilding=%d full=%d)",
                FGConfig.shipCadence() ? "ON" : "OFF", FGConfig.cheapPolicy(), FGConfig.catchUpTicksPerTick(), FGConfig.maxDebtTicks(),
                FactoryCadence.hasLocator(), FactoryCadence.eventsReceived, FactoryCadence.cheapEvents,
                FactoryCadence.rebuildingEvents, FactoryCadence.fullEvents));
        int ground = 0, groundBEs = 0;
        java.util.Map<Object, int[]> per = new java.util.HashMap<>();
        for (FactoryGraphManager m : FactoryGraphManager.all()) {
            for (FactoryGraph g : m.graphs.values()) {
                if (g.shipKey == null) { ground++; groundBEs += g.nodes.size(); }
                else { int[] a = per.computeIfAbsent(g.shipKey, k -> new int[2]); a[0]++; a[1] += g.parentedBEs; }
            }
            say(c, String.format(" [%s] parentedRebuilds=%d groundRebuilds=%d", m.level.dimension().location(), m.parentedRebuilds, m.groundRebuilds));
        }
        say(c, " ground graphs=" + ground + " nodes=" + groundBEs + " (never gated)");
        for (ShipGate gt : FactoryCadence.gates()) {
            int[] a = per.getOrDefault(gt.key, new int[2]);
            gt.graphs = a[0];
            gt.attached = a[1];
            say(c, " " + gt);
        }
        for (String e : FactoryCadence.recentEvents()) say(c, "  event " + e);
        return 1;
    }

    private static int policy(CommandContext<CommandSourceStack> c) {
        try {
            FGConfig.runtimePolicy = FGConfig.CheapPolicy.valueOf(StringArgumentType.getString(c, "policy").toUpperCase());
        } catch (Exception e) {
            c.getSource().sendFailure(Component.literal("policy must be slow|sleep|burst"));
            return 0;
        }
        return say(c, "cheap policy " + FGConfig.runtimePolicy);
    }

    private static Long networkAt(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        if (c.getSource().getLevel().getBlockEntity(pos) instanceof KineticBlockEntity be && be.network != null) return be.network;
        c.getSource().sendFailure(Component.literal("no kinetic network at " + pos.toShortString()));
        return null;
    }

    private static int parent(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Long net = networkAt(c);
        if (net == null) return 0;
        Object key = parseKey(StringArgumentType.getString(c, "key"));
        FactoryGraphManager m = FactoryGraphManager.get(c.getSource().getLevel());
        m.forcedParents.put(net.longValue(), key);
        m.markDirty(net);
        return say(c, "synthetic parent: network " + net + " -> ship " + key + " (applied on next graph rebuild)");
    }

    private static int unparent(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Long net = networkAt(c);
        if (net == null) return 0;
        FactoryGraphManager m = FactoryGraphManager.get(c.getSource().getLevel());
        m.forcedParents.remove(net.longValue());
        m.markDirty(net);
        return say(c, "network " + net + " unparented");
    }

    private static int setMode(CommandContext<CommandSourceStack> c, int period) {
        Object key = parseKey(StringArgumentType.getString(c, "key"));
        FactoryCadence.Mode mode;
        try { mode = FactoryCadence.Mode.valueOf(StringArgumentType.getString(c, "mode").toUpperCase()); }
        catch (Exception e) { c.getSource().sendFailure(Component.literal("mode must be FULL|CHEAP|REBUILDING")); return 0; }
        FactoryCadence.setShipMode(key, mode, period, -1);
        return say(c, "synthetic cadence event: " + key + " -> " + mode);
    }

    private static int cadenceInspect(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        if (!(c.getSource().getLevel().getBlockEntity(pos) instanceof SmartBlockEntity be)) return say(c, "no Create smart BE at " + pos.toShortString());
        CadenceDuck d = (CadenceDuck) be;
        ShipGate g = d.cfg$gate();
        Object located = FactoryCadence.locateShip(c.getSource().getLevel(), pos.asLong());
        return say(c, pos.toShortString() + " gate=" + (g == null ? "none (ground)" : g.key + " " + g.mode) + " debt=" + d.cfg$debt() + " locator=" + located);
    }

    private FactoryGraphCommand() {}
}
