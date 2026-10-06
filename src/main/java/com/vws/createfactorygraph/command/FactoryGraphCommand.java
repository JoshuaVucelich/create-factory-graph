package com.vws.createfactorygraph.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
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

    private FactoryGraphCommand() {}
}
