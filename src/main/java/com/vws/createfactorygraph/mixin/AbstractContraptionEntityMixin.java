package com.vws.createfactorygraph.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.vws.createfactorygraph.contraptions.ContraptionShapeCache;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

@Mixin(AbstractContraptionEntity.class)
public abstract class AbstractContraptionEntityMixin {
    @Inject(method = "setBlock", at = @At("TAIL"))
    private void cfg$invalidate(BlockPos localPos, StructureTemplate.StructureBlockInfo newInfo, CallbackInfo ci) {
        ContraptionShapeCache.invalidate(((AbstractContraptionEntity) (Object) this).getContraption());
    }
}
