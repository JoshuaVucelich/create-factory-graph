package com.vws.createfactorygraph.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.vws.createfactorygraph.FGConfig;
import com.vws.createfactorygraph.graph.FactoryGraphManager;
import com.vws.createfactorygraph.profile.KineticTickProfiler;

import net.minecraft.server.level.ServerLevel;

/**
 * Jammed-belt backoff. A belt counts as jammed only when NOTHING about its items changed for
 * jamDetectTicks (positions, side offsets, stacks, counts, processing) and no item is locked
 * by a processor, nothing is queued for insert/remove and no entity rides it. While jammed the
 * inventory tick runs every 1,2,4..maxBackoffTicks ticks; any change or insert resumes full rate.
 * Items are never moved, dropped or ejected by this code - skipped ticks simply don't run.
 */
@Mixin(BeltInventory.class)
public abstract class BeltInventoryMixin {
    @Shadow @Final BeltBlockEntity belt;
    @Shadow @Final private List<TransportedItemStack> items;
    @Shadow @Final List<TransportedItemStack> toInsert;
    @Shadow @Final List<TransportedItemStack> toRemove;
    @Shadow TransportedItemStack lazyClientItem;

    @Unique private long cfg$fp;
    @Unique private boolean cfg$hasFp;
    @Unique private int cfg$still;
    @Unique private int cfg$backoff;
    @Unique private int cfg$wait;

    @Unique
    private boolean cfg$hasPassengers() {
        return belt.passengers != null && !belt.passengers.isEmpty();
    }

    @Unique
    private void cfg$resume(ServerLevel sl) {
        if (cfg$backoff > 0) {
            FactoryGraphManager m = FactoryGraphManager.get(sl);
            m.beltResumes++;
            m.jammedBelts.remove((BeltInventory) (Object) this);
        }
        cfg$backoff = 0;
        cfg$wait = 0;
        cfg$still = 0;
        cfg$hasFp = false;
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void cfg$head(CallbackInfo ci) {
        if (!(belt.getLevel() instanceof ServerLevel sl)) return;
        if (cfg$wait > 0) {
            if (!FGConfig.enabled() || !FGConfig.jamBackoff() || !toInsert.isEmpty() || !toRemove.isEmpty()
                    || lazyClientItem != null || cfg$hasPassengers() || belt.getSpeed() == 0) {
                cfg$resume(sl);
            } else {
                cfg$wait--;
                FactoryGraphManager.get(sl).beltBackoffSkips++;
                if (KineticTickProfiler.active) KineticTickProfiler.beltInvSkipped++;
                ci.cancel();
                return;
            }
        }
        if (KineticTickProfiler.active) KineticTickProfiler.beltInvTicks++;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void cfg$tail(CallbackInfo ci) {
        if (!(belt.getLevel() instanceof ServerLevel sl)) return;
        if (!FGConfig.enabled() || !FGConfig.jamBackoff()) {
            if (cfg$backoff > 0 || cfg$hasFp) cfg$resume(sl);
            return;
        }
        boolean eligible = belt.getSpeed() != 0 && !items.isEmpty() && toInsert.isEmpty() && toRemove.isEmpty()
                && lazyClientItem == null && !cfg$hasPassengers();
        long fp = 0;
        if (eligible) {
            long h = items.size();
            for (TransportedItemStack t : items) {
                if (t.locked || t.lockedExternally) {
                    eligible = false;
                    break;
                }
                h = h * 31 + Float.floatToIntBits(t.beltPosition);
                h = h * 31 + Float.floatToIntBits(t.sideOffset);
                h = h * 31 + System.identityHashCode(t.stack);
                h = h * 31 + t.stack.getCount();
                h = h * 31 + t.processingTime;
                h = h * 31 + (t.processedBy == null ? 0 : t.processedBy.hashCode());
            }
            fp = h;
        }
        if (eligible && cfg$hasFp && fp == cfg$fp) {
            if (++cfg$still >= FGConfig.jamDetectTicks()) {
                if (cfg$backoff == 0) {
                    FactoryGraphManager m = FactoryGraphManager.get(sl);
                    m.beltJamEvents++;
                    m.jammedBelts.add((BeltInventory) (Object) this);
                    cfg$backoff = 1;
                } else {
                    cfg$backoff = Math.min(cfg$backoff * 2, FGConfig.maxBackoffTicks());
                }
                cfg$wait = cfg$backoff;
            }
        } else if (cfg$backoff > 0 || cfg$still > 0) {
            boolean keep = eligible;
            cfg$resume(sl);
            if (keep) {
                cfg$fp = fp;
                cfg$hasFp = true;
            }
            return;
        }
        cfg$fp = fp;
        cfg$hasFp = eligible;
    }

    @Inject(method = "addItem", at = @At("TAIL"))
    private void cfg$added(TransportedItemStack stack, CallbackInfo ci) {
        if (belt.getLevel() instanceof ServerLevel sl && (cfg$backoff > 0 || cfg$wait > 0)) cfg$resume(sl);
    }
}
