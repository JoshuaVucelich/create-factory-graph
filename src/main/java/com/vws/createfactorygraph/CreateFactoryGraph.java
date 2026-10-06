package com.vws.createfactorygraph;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.vws.createfactorygraph.command.FactoryGraphCommand;
import com.vws.createfactorygraph.graph.FactoryGraphManager;
import com.vws.createfactorygraph.profile.KineticTickProfiler;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Create Factory Graph (Phase 2).
 * Server-side: caches each Create kinetic network as a graph, rebuilt only on kinetic
 * topology/power changes, and uses it to (a) take settled decorative relays and belt
 * segments off the block-entity ticker list, (b) back off jammed belt inventories.
 * No client entrypoint, no client-only classes.
 */
@Mod(CreateFactoryGraph.MOD_ID)
public class CreateFactoryGraph {
    public static final String MOD_ID = "create_factory_graph";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateFactoryGraph(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, FGConfig.SPEC);
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onLevelTickPost);
        NeoForge.EVENT_BUS.addListener(this::onServerTickPre);
        NeoForge.EVENT_BUS.addListener(this::onServerTickPost);
        NeoForge.EVENT_BUS.addListener(this::onLevelUnload);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("{} phase-2 factory graph loaded (Create 6.0.10 mixins active)", MOD_ID);
    }

    private void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("{} ready; graph mode {}", MOD_ID, FGConfig.enabled() ? "ENABLED" : "disabled");
    }

    private void onServerStopping(ServerStoppingEvent event) {
        FactoryGraphManager.clearAll();
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        FactoryGraphCommand.register(event.getDispatcher());
    }

    private void onLevelTickPost(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel sl) {
            FactoryGraphManager.get(sl).endTick();
        }
    }

    private void onServerTickPre(ServerTickEvent.Pre event) {
        KineticTickProfiler.serverTickStart();
    }

    private void onServerTickPost(ServerTickEvent.Post event) {
        KineticTickProfiler.serverTickEnd();
    }

    private void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel sl) {
            FactoryGraphManager.remove(sl);
        }
    }
}
