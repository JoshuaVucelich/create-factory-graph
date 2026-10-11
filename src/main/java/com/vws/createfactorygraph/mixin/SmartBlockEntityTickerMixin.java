package com.vws.createfactorygraph.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntityTicker;
import com.vws.createfactorygraph.CreateFactoryGraph;
import com.vws.createfactorygraph.cadence.CadenceDuck;
import com.vws.createfactorygraph.cadence.ShipCadenceGate;
import com.vws.createfactorygraph.profile.KineticTickProfiler;
import com.vws.createfactorygraph.safety.Safety;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's ticker for every SmartBlockEntity.
 *  - Phase 4: ship-parented BEs (gate != null) are deferred / caught up to the hull cadence.
 *    Ground BEs have gate == null and pass straight through.
 *  - Opt-in timing of Create kinetic BE ticks (A/B profiling).
 */
@Mixin(SmartBlockEntityTicker.class)
public abstract class SmartBlockEntityTickerMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void cfg$start(Level level, BlockPos pos, BlockState state, BlockEntity be, CallbackInfo ci) {
        if (be instanceof CadenceDuck duck && duck.cfg$gate() != null && !level.isClientSide) {
            boolean skip = false;
            boolean failed = false;
            try {
                skip = ShipCadenceGate.beforeTick(level, (SmartBlockEntity) be, duck);
            } catch (Throwable t) {
                failed = true;
                duck.cfg$setDebt(0);
                duck.cfg$setGate(null);
                if (Safety.noteFailOpen()) CreateFactoryGraph.LOGGER.warn("ticker gate failed open at {}: {}", pos, t.toString());
            }
            if (Safety.skipOriginal(skip, failed)) {
                ci.cancel();
                return;
            }
        }
        if (KineticTickProfiler.active && be instanceof KineticBlockEntity && !level.isClientSide) KineticTickProfiler.beStart();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void cfg$end(Level level, BlockPos pos, BlockState state, BlockEntity be, CallbackInfo ci) {
        if (KineticTickProfiler.active && be instanceof KineticBlockEntity && !level.isClientSide) KineticTickProfiler.beEnd();
    }
}
