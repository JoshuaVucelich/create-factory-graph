package com.vws.createfactorygraph.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ContraptionCollider;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.vws.createfactorygraph.contraptions.ContraptionShapeCache;
import com.vws.createfactorygraph.profile.AreaProfiler;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

@Mixin(ContraptionCollider.class)
public abstract class ContraptionColliderMixin {
    @Inject(method = "collideEntities", at = @At("HEAD"))
    private static void cfg$collideBegin(AbstractContraptionEntity contraptionEntity, CallbackInfo ci) {
        AreaProfiler.contraptionCollide.begin();
    }

    @Inject(method = "collideEntities", at = @At("RETURN"))
    private static void cfg$collideEnd(AbstractContraptionEntity contraptionEntity, CallbackInfo ci) {
        AreaProfiler.contraptionCollide.end();
    }

    @Inject(method = "getPotentiallyCollidedShapes", at = @At("HEAD"), cancellable = true)
    private static void cfg$replay(Level world, Contraption contraption, AABB localBB, Shapes.DoubleLineConsumer out, CallbackInfo ci) {
        AreaProfiler.contraptionShapes.begin();
        if (ContraptionShapeCache.replay(contraption, localBB, out)) {
            AreaProfiler.contraptionShapes.end();
            ci.cancel();
        } else {
            ContraptionShapeCache.begin(contraption);
        }
    }

    @Redirect(method = "getPotentiallyCollidedShapes", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/VoxelShape;forAllBoxes(Lnet/minecraft/world/phys/shapes/Shapes$DoubleLineConsumer;)V"))
    private static void cfg$record(VoxelShape shape, Shapes.DoubleLineConsumer consumer) {
        if (!ContraptionShapeCache.recording()) {
            shape.forAllBoxes(consumer);
            return;
        }
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            ContraptionShapeCache.record(x1, y1, z1, x2, y2, z2);
            consumer.consume(x1, y1, z1, x2, y2, z2);
        });
    }

    @Inject(method = "getPotentiallyCollidedShapes", at = @At("RETURN"))
    private static void cfg$end(Level world, Contraption contraption, AABB localBB, Shapes.DoubleLineConsumer out, CallbackInfo ci) {
        ContraptionShapeCache.end(contraption, localBB);
        AreaProfiler.contraptionShapes.end();
    }
}
