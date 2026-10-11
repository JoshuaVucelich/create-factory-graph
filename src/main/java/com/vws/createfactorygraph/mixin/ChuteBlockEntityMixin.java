package com.vws.createfactorygraph.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import com.vws.createfactorygraph.CreateFactoryGraph;
import com.vws.createfactorygraph.logistics.LogisticsCache;
import com.vws.createfactorygraph.profile.AreaProfiler;
import com.vws.createfactorygraph.safety.Safety;

@Mixin(ChuteBlockEntity.class)
public abstract class ChuteBlockEntityMixin {
    @Shadow public abstract float getItemMotion();

    @Inject(method = "tick", at = @At("HEAD"))
    private void cfg$begin(CallbackInfo ci) {
        AreaProfiler.chute.begin();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void cfg$end(CallbackInfo ci) {
        AreaProfiler.chute.end();
    }

    @Inject(method = "canDirectlyInsert", at = @At("HEAD"), cancellable = true)
    private void cfg$hit(CallbackInfoReturnable<Boolean> cir) {
        try {
            Boolean cached = LogisticsCache.chuteInsert((ChuteBlockEntity) (Object) this, getItemMotion() > 0);
            if (cached != null) cir.setReturnValue(cached);
        } catch (Throwable t) {
            if (Safety.noteFailOpen()) CreateFactoryGraph.LOGGER.warn("chute insert cache failed open: {}", t.toString());
        }
    }

    @Inject(method = "canDirectlyInsert", at = @At("RETURN"))
    private void cfg$store(CallbackInfoReturnable<Boolean> cir) {
        LogisticsCache.storeChuteInsert((ChuteBlockEntity) (Object) this, getItemMotion() > 0, cir.getReturnValueZ());
    }
}
