package com.vws.createfactorygraph.hooks;

import java.util.Set;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.gearbox.GearboxBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.BracketedKineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.SimpleKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryTrackerBehaviour;
import com.vws.createfactorygraph.FGConfig;
import com.vws.createfactorygraph.mixin.accessor.BeltBlockEntityAccessor;
import com.vws.createfactorygraph.mixin.accessor.KineticBlockEntityAccessor;
import com.vws.createfactorygraph.mixin.accessor.KineticEffectHandlerAccessor;
import com.vws.createfactorygraph.mixin.accessor.SmartBlockEntityAccessor;

import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Decides which Create kinetic block entities are "decorative" (no inventory, no per-tick logic)
 * and may be taken off the ticker list once settled. Exact-class + exact-type matching only, so
 * addon subclasses with their own tick logic are never put to sleep. Pinned to Create 6.0.10.
 */
public final class DecorativePolicy {
    public static final byte UNKNOWN = 0, RELAY = 1, BELT_SEGMENT = 2, NEVER = 3;

    /** Behaviours verified (Create 6.0.10) to have no tick()/lazyTick() override. */
    private static final Set<Class<?>> QUIET_BEHAVIOURS = Set.of(
            BracketedBlockEntityBehaviour.class,
            VersionedInventoryTrackerBehaviour.class,
            DirectBeltInputBehaviour.class,
            TransportedItemStackHandlerBehaviour.class);

    public static byte classify(KineticBlockEntity be) {
        Class<?> c = be.getClass();
        BlockEntityType<?> t = be.getType();
        boolean typeOk;
        byte kind = RELAY;
        if (c == BracketedKineticBlockEntity.class) {
            typeOk = t == AllBlockEntityTypes.BRACKETED_KINETIC.get();
        } else if (c == SimpleKineticBlockEntity.class) {
            typeOk = t == AllBlockEntityTypes.ENCASED_COGWHEEL.get() || t == AllBlockEntityTypes.ENCASED_LARGE_COGWHEEL.get();
        } else if (c == KineticBlockEntity.class) {
            typeOk = t == AllBlockEntityTypes.ENCASED_SHAFT.get();
        } else if (c == GearboxBlockEntity.class) {
            typeOk = t == AllBlockEntityTypes.GEARBOX.get();
        } else if (c == BeltBlockEntity.class) {
            typeOk = t == AllBlockEntityTypes.BELT.get();
            kind = BELT_SEGMENT;
        } else {
            return NEVER;
        }
        if (!typeOk) return NEVER;
        for (BlockEntityBehaviour b : be.getAllBehaviours()) {
            if (!QUIET_BEHAVIOURS.contains(b.getClass())) return NEVER;
        }
        return kind;
    }

    /** Everything a dormant node would otherwise still have done in KineticBlockEntity.tick(). */
    public static boolean isQuiet(KineticBlockEntity be, byte kind) {
        if (kind == RELAY) {
            if (!FGConfig.dormantRelays()) return false;
        } else if (kind == BELT_SEGMENT) {
            if (!FGConfig.dormantBeltSegments()) return false;
            BeltBlockEntity belt = (BeltBlockEntity) be;
            if (belt.isController() || belt.beltLength == 0) return false;
            if (((BeltBlockEntityAccessor) belt).cfg$getItemHandler() == null) return false;
        } else {
            return false;
        }
        if (be.isVirtual() || be.isRemoved()) return false;
        if (!((SmartBlockEntityAccessor) be).cfg$isInitialized()) return false;
        if (be.needsSpeedUpdate() || be.networkDirty) return false;
        if (be.getFlickerScore() > 0) return false;
        KineticBlockEntityAccessor acc = (KineticBlockEntityAccessor) be;
        if (((KineticEffectHandlerAccessor) acc.cfg$getEffects()).cfg$getParticleSpawnCountdown() > 0) return false;
        return true;
    }

    private DecorativePolicy() {}
}
