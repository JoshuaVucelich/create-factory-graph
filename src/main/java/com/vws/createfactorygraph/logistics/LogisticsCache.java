package com.vws.createfactorygraph.logistics;

import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.vws.createfactorygraph.FGConfig;
import com.vws.createfactorygraph.profile.AreaProfiler;
import com.vws.createfactorygraph.world.WorldEpoch;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Memoises logistics lookups that Create repeats every tick and that only change when a block
 * changes or (for chutes) the air-flow sign changes. Misses call Create's own methods.
 */
public final class LogisticsCache {
    private static final IdentityHashMap<Level, Long2ObjectOpenHashMap<Seg>> SEGMENTS = new IdentityHashMap<>();
    private static final IdentityHashMap<Level, Long2ObjectOpenHashMap<Beh>> BEHAVIOURS = new IdentityHashMap<>();
    private static final IdentityHashMap<Level, Long2ObjectOpenHashMap<Bool>> FUNNEL_ABOVE = new IdentityHashMap<>();
    private static long seenEpoch = Long.MIN_VALUE;

    private static final class Seg {
        long epoch;
        WeakReference<BeltBlockEntity> ref;
        boolean absent;
    }

    private static final class Beh {
        long epoch;
        BehaviourType<?> type;
        WeakReference<BlockEntityBehaviour> ref;
        boolean absent;
    }

    private static final class Bool {
        long epoch;
        boolean value;
    }

    private static final class ChuteSlot {
        long epoch;
        boolean motionUp;
        boolean value;
        boolean present;
    }

    private static final java.util.WeakHashMap<ChuteBlockEntity, ChuteSlot> CHUTES = new java.util.WeakHashMap<>();

    public static boolean active() {
        return FGConfig.enabled() && FGConfig.logisticsCache();
    }

    private static void rollEpoch() {
        long now = WorldEpoch.get();
        if (now != seenEpoch) {
            seenEpoch = now;
            SEGMENTS.clear();
            BEHAVIOURS.clear();
            FUNNEL_ABOVE.clear();
        }
    }

    public static BeltBlockEntity segmentBE(LevelAccessor level, BlockPos pos) {
        if (!(level instanceof Level lvl) || lvl.isClientSide || !active()) return BeltHelper.getSegmentBE(level, pos);
        rollEpoch();
        long key = pos.asLong();
        Long2ObjectOpenHashMap<Seg> map = SEGMENTS.computeIfAbsent(lvl, l -> new Long2ObjectOpenHashMap<>());
        Seg seg = map.get(key);
        long epoch = WorldEpoch.get();
        if (seg != null && seg.epoch == epoch) {
            if (seg.absent) {
                AreaProfiler.logisticsHits++;
                return null;
            }
            BeltBlockEntity be = seg.ref.get();
            if (be != null && !be.isRemoved()) {
                AreaProfiler.logisticsHits++;
                return be;
            }
        }
        BeltBlockEntity be = BeltHelper.getSegmentBE(level, pos);
        Seg stored = new Seg();
        stored.epoch = epoch;
        stored.absent = be == null;
        stored.ref = new WeakReference<>(be);
        map.put(key, stored);
        AreaProfiler.logisticsMisses++;
        return be;
    }

    public static BlockEntityBehaviour behaviour(BlockGetter level, BlockPos pos, BehaviourType<?> type) {
        if (!(level instanceof Level lvl) || lvl.isClientSide || !active()) return BlockEntityBehaviour.get(level, pos, type);
        rollEpoch();
        long key = pos.asLong() ^ (type.hashCode() * 0x9E3779B97F4A7C15L);
        Long2ObjectOpenHashMap<Beh> map = BEHAVIOURS.computeIfAbsent(lvl, l -> new Long2ObjectOpenHashMap<>());
        Beh hit = map.get(key);
        long epoch = WorldEpoch.get();
        if (hit != null && hit.epoch == epoch && hit.type == type) {
            if (hit.absent) {
                AreaProfiler.logisticsHits++;
                return null;
            }
            BlockEntityBehaviour beh = hit.ref.get();
            if (beh != null && beh.blockEntity != null && !beh.blockEntity.isRemoved()) {
                AreaProfiler.logisticsHits++;
                return beh;
            }
        }
        BlockEntityBehaviour beh = BlockEntityBehaviour.get(level, pos, type);
        Beh stored = new Beh();
        stored.epoch = epoch;
        stored.type = type;
        stored.absent = beh == null;
        stored.ref = new WeakReference<>(beh);
        map.put(key, stored);
        AreaProfiler.logisticsMisses++;
        return beh;
    }

    /** True only when the block above is known not to be a funnel. */
    public static boolean noFunnelAbove(Level level, BlockPos pos) {
        if (level == null || level.isClientSide || !active()) return false;
        rollEpoch();
        BlockPos above = pos.above();
        long key = above.asLong();
        Long2ObjectOpenHashMap<Bool> map = FUNNEL_ABOVE.computeIfAbsent(level, l -> new Long2ObjectOpenHashMap<>());
        Bool hit = map.get(key);
        long epoch = WorldEpoch.get();
        if (hit != null && hit.epoch == epoch) {
            AreaProfiler.logisticsHits++;
            return hit.value;
        }
        BlockState state = level.getBlockState(above);
        boolean none = AbstractFunnelBlock.getFunnelFacing(state) == null;
        Bool stored = new Bool();
        stored.epoch = epoch;
        stored.value = none;
        map.put(key, stored);
        AreaProfiler.logisticsMisses++;
        return none;
    }

    /** @return cached canDirectlyInsert, or null to run the original */
    public static Boolean chuteInsert(ChuteBlockEntity chute, boolean motionUp) {
        if (!active() || chute.getLevel() == null || chute.getLevel().isClientSide) return null;
        ChuteSlot slot = CHUTES.get(chute);
        if (slot != null && slot.present && slot.epoch == WorldEpoch.get() && slot.motionUp == motionUp) {
            AreaProfiler.logisticsHits++;
            return slot.value;
        }
        return null;
    }

    public static void storeChuteInsert(ChuteBlockEntity chute, boolean motionUp, boolean value) {
        if (!active() || chute.getLevel() == null || chute.getLevel().isClientSide) return;
        ChuteSlot slot = CHUTES.computeIfAbsent(chute, c -> new ChuteSlot());
        long epoch = WorldEpoch.get();
        if (slot.present && slot.epoch == epoch && slot.motionUp == motionUp && slot.value == value) return;
        slot.epoch = epoch;
        slot.motionUp = motionUp;
        slot.value = value;
        slot.present = true;
        AreaProfiler.logisticsMisses++;
    }

    private LogisticsCache() {}
}
