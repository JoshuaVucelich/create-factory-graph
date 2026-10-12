package com.vws.benchprobe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionCollider;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.piston.LinearActuatorBlockEntity;
import com.simibubi.create.content.fluids.FluidNetwork;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.BeltFunnelInteractionHandler;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.GlobalLogisticsManager;
import com.simibubi.create.content.trains.GlobalRailwayManager;
import com.simibubi.create.foundation.events.CommonEvents;
import com.vws.benchprobe.Sections;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * One mixin target per class would be a pile of files. Each method below is a
 * separate mixin class in this file's companion classes, registered by name.
 */
public final class SectionTimeMixin {
    private SectionTimeMixin() {}
}

@Mixin(Level.class)
abstract class TimeBlockEntitiesMixin {
    @Inject(method = "tickBlockEntities", at = @At("HEAD"))
    private void bench$begin(CallbackInfo ci) { Sections.blockEntities.begin(); }

    @Inject(method = "tickBlockEntities", at = @At("RETURN"))
    private void bench$end(CallbackInfo ci) { Sections.blockEntities.end(); }

    @org.spongepowered.asm.mixin.injection.Redirect(method = "tickBlockEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;shouldTickBlocksAt(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean bench$should(Level level, BlockPos pos) {
        Sections.shouldTick.begin();
        boolean tick = level.shouldTickBlocksAt(pos);
        Sections.shouldTick.end();
        return tick;
    }
}

@Mixin(net.minecraft.server.level.ServerLevel.class)
abstract class TimeEntityTickMixin {
    @Inject(method = "tickNonPassenger", at = @At("HEAD"))
    private void bench$begin(Entity entity, CallbackInfo ci) { Sections.entityTick.begin(); }

    @Inject(method = "tickNonPassenger", at = @At("RETURN"))
    private void bench$end(Entity entity, CallbackInfo ci) { Sections.entityTick.end(); }
}

@Mixin(AbstractContraptionEntity.class)
abstract class TimeContraptionEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void bench$begin(CallbackInfo ci) { Sections.contraptionEntity.begin(); }

    @Inject(method = "tick", at = @At("RETURN"))
    private void bench$end(CallbackInfo ci) { Sections.contraptionEntity.end(); }
}

@Mixin(CommonEvents.class)
abstract class TimeCommonEventsMixin {
    @Inject(method = "onServerWorldTick", at = @At("HEAD"))
    private static void bench$wb(LevelTickEvent.Post event, CallbackInfo ci) { Sections.worldEvents.begin(); }

    @Inject(method = "onServerWorldTick", at = @At("RETURN"))
    private static void bench$we(LevelTickEvent.Post event, CallbackInfo ci) { Sections.worldEvents.end(); }

    @Inject(method = "onServerTick", at = @At("HEAD"))
    private static void bench$sb(ServerTickEvent.Post event, CallbackInfo ci) { Sections.serverEvents.begin(); }

    @Inject(method = "onServerTick", at = @At("RETURN"))
    private static void bench$se(ServerTickEvent.Post event, CallbackInfo ci) { Sections.serverEvents.end(); }
}

@Mixin(ContraptionCollider.class)
abstract class TimeCollideMixin {
    @Inject(method = "collideEntities", at = @At("HEAD"))
    private static void bench$begin(AbstractContraptionEntity entity, CallbackInfo ci) { Sections.collide.begin(); }

    @Inject(method = "collideEntities", at = @At("RETURN"))
    private static void bench$end(AbstractContraptionEntity entity, CallbackInfo ci) { Sections.collide.end(); }
}

@Mixin(GlobalRailwayManager.class)
abstract class TimeRailMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void bench$begin(Level level, CallbackInfo ci) { Sections.railways.begin(); }

    @Inject(method = "tick", at = @At("RETURN"))
    private void bench$end(Level level, CallbackInfo ci) { Sections.railways.end(); }
}

@Mixin(GlobalLogisticsManager.class)
abstract class TimeLogisticsMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void bench$begin(Level level, CallbackInfo ci) { Sections.logistics.begin(); }

    @Inject(method = "tick", at = @At("RETURN"))
    private void bench$end(Level level, CallbackInfo ci) { Sections.logistics.end(); }
}

@Mixin(KineticBlockEntity.class)
abstract class TimeKineticMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void bench$begin(CallbackInfo ci) { Sections.kinetic.begin(); }

    @Inject(method = "tick", at = @At("RETURN"))
    private void bench$end(CallbackInfo ci) { Sections.kinetic.end(); }
}

@Mixin(BeltInventory.class)
abstract class TimeBeltMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void bench$begin(CallbackInfo ci) { Sections.belt.begin(); }

    @Inject(method = "tick", at = @At("RETURN"))
    private void bench$end(CallbackInfo ci) { Sections.belt.end(); }
}

@Mixin(BeltFunnelInteractionHandler.class)
abstract class TimeBeltFunnelMixin {
    @Inject(method = "checkForFunnels", at = @At("HEAD"))
    private static void bench$begin(BeltInventory inventory, TransportedItemStack stack, float nextOffset, CallbackInfoReturnable<Boolean> ci) {
        Sections.beltFunnel.begin();
    }

    @Inject(method = "checkForFunnels", at = @At("RETURN"))
    private static void bench$end(BeltInventory inventory, TransportedItemStack stack, float nextOffset, CallbackInfoReturnable<Boolean> ci) {
        Sections.beltFunnel.end();
    }
}

@Mixin(EncasedFanBlockEntity.class)
abstract class TimeFanMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void bench$begin(CallbackInfo ci) { Sections.fan.begin(); }

    @Inject(method = "tick", at = @At("RETURN"))
    private void bench$end(CallbackInfo ci) { Sections.fan.end(); }
}

@Mixin(AirCurrent.class)
abstract class TimeFanEntitiesMixin {
    @Inject(method = "findEntities", at = @At("HEAD"))
    private void bench$begin(CallbackInfo ci) { Sections.fanEntities.begin(); }

    @Inject(method = "findEntities", at = @At("RETURN"))
    private void bench$end(CallbackInfo ci) { Sections.fanEntities.end(); }
}

@Mixin(DeployerBlockEntity.class)
abstract class TimeDeployerMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void bench$begin(CallbackInfo ci) { Sections.deployer.begin(); }

    @Inject(method = "tick", at = @At("RETURN"))
    private void bench$end(CallbackInfo ci) { Sections.deployer.end(); }
}

@Mixin(PressingBehaviour.class)
abstract class TimePressMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void bench$begin(CallbackInfo ci) { Sections.press.begin(); }

    @Inject(method = "tick", at = @At("RETURN"))
    private void bench$end(CallbackInfo ci) { Sections.press.end(); }
}

@Mixin(MechanicalMixerBlockEntity.class)
abstract class TimeMixerMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void bench$begin(CallbackInfo ci) { Sections.mixer.begin(); }

    @Inject(method = "tick", at = @At("RETURN"))
    private void bench$end(CallbackInfo ci) { Sections.mixer.end(); }
}

@Mixin(FunnelBlockEntity.class)
abstract class TimeFunnelMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void bench$begin(CallbackInfo ci) { Sections.funnel.begin(); }

    @Inject(method = "tick", at = @At("RETURN"))
    private void bench$end(CallbackInfo ci) { Sections.funnel.end(); }
}

@Mixin(DepotBehaviour.class)
abstract class TimeDepotMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void bench$begin(CallbackInfo ci) { Sections.depot.begin(); }

    @Inject(method = "tick", at = @At("RETURN"))
    private void bench$end(CallbackInfo ci) { Sections.depot.end(); }
}

@Mixin(ChuteBlockEntity.class)
abstract class TimeChuteMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void bench$begin(CallbackInfo ci) { Sections.chute.begin(); }

    @Inject(method = "tick", at = @At("RETURN"))
    private void bench$end(CallbackInfo ci) { Sections.chute.end(); }
}

@Mixin(FluidTransportBehaviour.class)
abstract class TimeFluidTransportMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void bench$begin(CallbackInfo ci) { Sections.fluidTransport.begin(); }

    @Inject(method = "tick", at = @At("RETURN"))
    private void bench$end(CallbackInfo ci) { Sections.fluidTransport.end(); }
}

@Mixin(FluidNetwork.class)
abstract class TimeFluidNetworkMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void bench$begin(CallbackInfo ci) { Sections.fluidNetwork.begin(); }

    @Inject(method = "tick", at = @At("RETURN"))
    private void bench$end(CallbackInfo ci) { Sections.fluidNetwork.end(); }
}

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
abstract class TimeBoundTickerMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void bench$begin(CallbackInfo ci) { Sections.beTicker.begin(); }

    @Inject(method = "tick", at = @At("RETURN"))
    private void bench$end(CallbackInfo ci) { Sections.beTicker.end(); }
}

@Mixin(MechanicalBearingBlockEntity.class)
abstract class TimeBearingMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void bench$begin(CallbackInfo ci) { Sections.bearing.begin(); }

    @Inject(method = "tick", at = @At("RETURN"))
    private void bench$end(CallbackInfo ci) { Sections.bearing.end(); }
}

@Mixin(LinearActuatorBlockEntity.class)
abstract class TimeActuatorMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void bench$begin(CallbackInfo ci) { Sections.actuator.begin(); }

    @Inject(method = "tick", at = @At("RETURN"))
    private void bench$end(CallbackInfo ci) { Sections.actuator.end(); }
}
