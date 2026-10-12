package com.vws.benchprobe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.contraptions.piston.LinearActuatorBlockEntity;
import com.vws.benchprobe.Pause;

@Mixin(LinearActuatorBlockEntity.class)
public abstract class ActuatorPauseMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void bench$pause(CallbackInfo ci) {
        if (Pause.skip()) ci.cancel();
    }
}
