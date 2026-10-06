package com.vws.createfactorygraph.hooks;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.vws.createfactorygraph.FGConfig;
import com.vws.createfactorygraph.graph.FactoryGraphManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Static entry points called from mixins. All no-ops on the client. */
public final class FactoryGraphHooks {

    /** End of KineticBlockEntity.tick() on the server: count quiet ticks, queue for dormancy. */
    public static void afterKineticTick(KineticBlockEntity be) {
        if (!(be.getLevel() instanceof ServerLevel sl) || !FGConfig.enabled()) return;
        KineticNodeDuck duck = (KineticNodeDuck) be;
        byte cand = duck.cfg$candidate();
        if (cand == DecorativePolicy.UNKNOWN) {
            cand = DecorativePolicy.classify(be);
            duck.cfg$setCandidate(cand);
        }
        if (cand == DecorativePolicy.NEVER) return;
        if (duck.cfg$isDormant()) {
            // Vanilla re-added our ticker (e.g. blockstate change) without telling us.
            FactoryGraphManager.get(sl).forgetDormant(be);
        }
        if (DecorativePolicy.isQuiet(be, cand)) {
            int q = duck.cfg$quietTicks() + 1;
            duck.cfg$setQuietTicks(q);
            if (q >= FGConfig.settleTicks() && !duck.cfg$isQueued()) {
                duck.cfg$setQueued(true);
                FactoryGraphManager.get(sl).queueSleep(be);
            }
        } else {
            duck.cfg$setQuietTicks(0);
        }
    }

    /** Kinetic state of this BE changed (speed, source, network...). Wake it if dormant. */
    public static void touch(KineticBlockEntity be) {
        Level level = be.getLevel();
        if (!(level instanceof ServerLevel sl)) return;
        KineticNodeDuck duck = (KineticNodeDuck) be;
        duck.cfg$setQuietTicks(0);
        if (duck.cfg$isDormant()) FactoryGraphManager.get(sl).wake(be, "state");
    }

    /** Kinetic connectivity changed around this BE: mark its network for graph rebuild. */
    public static void topology(KineticBlockEntity be) {
        if (!(be.getLevel() instanceof ServerLevel sl)) return;
        touch(be);
        if (be.network != null) FactoryGraphManager.get(sl).markDirty(be.network);
    }

    public static void networkChanged(KineticBlockEntity be, Long networkId) {
        if (networkId == null || !(be.getLevel() instanceof ServerLevel sl)) return;
        FactoryGraphManager.get(sl).markDirty(networkId);
    }

    /** After KineticNetwork.remove(): Create flags the first remaining member networkDirty; it must tick. */
    public static void afterNetworkRemove(KineticNetwork network, KineticBlockEntity removed) {
        if (!(removed.getLevel() instanceof ServerLevel sl)) return;
        if (network.id != null) FactoryGraphManager.get(sl).markDirty(network.id);
        for (KineticBlockEntity m : network.members.keySet()) {
            if (m.networkDirty) touch(m);
            break; // Create only dirties the first member (same HashMap iteration order)
        }
    }

    /** SmartBlockEntity.invalidate(): BE removed or chunk unloaded. */
    public static void invalidated(BlockEntity be) {
        if (be instanceof KineticBlockEntity kbe && be.getLevel() instanceof ServerLevel sl) {
            FactoryGraphManager.get(sl).forgetDormant(kbe);
            if (kbe.network != null) FactoryGraphManager.get(sl).markDirty(kbe.network);
        }
    }

    /** LevelChunk.updateBlockEntityTicker() ran for this BE: it is ticking again. */
    public static void tickerRebound(BlockEntity be) {
        if (be instanceof KineticBlockEntity kbe && be.getLevel() instanceof ServerLevel sl
                && ((KineticNodeDuck) kbe).cfg$isDormant()) {
            FactoryGraphManager.get(sl).forgetDormant(kbe);
        }
    }

    private FactoryGraphHooks() {}
}
