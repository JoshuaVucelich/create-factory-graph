package com.vws.createfactorygraph.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.vws.createfactorygraph.logistics.LogisticsCache;
import com.vws.createfactorygraph.profile.AreaProfiler;
import com.vws.createfactorygraph.safety.Safety;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

@Mixin(DepotBehaviour.class)
public abstract class DepotBehaviourMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void cfg$begin(CallbackInfo ci) {
        AreaProfiler.depot.begin();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void cfg$end(CallbackInfo ci) {
        AreaProfiler.depot.end();
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/blockEntity/behaviour/BlockEntityBehaviour;get(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lcom/simibubi/create/foundation/blockEntity/behaviour/BehaviourType;)Lcom/simibubi/create/foundation/blockEntity/behaviour/BlockEntityBehaviour;"))
    private BlockEntityBehaviour cfg$behaviour(BlockGetter level, BlockPos pos, BehaviourType<?> type) {
        return LogisticsCache.behaviour(level, pos, type);
    }

    /** No funnel above means Create returns false after one block-state read and no item move. */
    @Inject(method = "handleBeltFunnelOutput", at = @At("HEAD"), cancellable = true)
    private void cfg$noFunnel(CallbackInfoReturnable<Boolean> cir) {
        try {
            DepotBehaviour self = (DepotBehaviour) (Object) this;
            if (self.getWorld() != null && LogisticsCache.noFunnelAbove(self.getWorld(), self.getPos())) {
                cir.setReturnValue(false);
            }
        } catch (Throwable t) {
            Safety.noteFailOpen();
        }
    }
}
