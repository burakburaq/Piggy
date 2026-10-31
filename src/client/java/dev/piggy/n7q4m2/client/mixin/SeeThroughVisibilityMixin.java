package dev.piggy.n7q4m2.client.mixin;

import dev.piggy.n7q4m2.client.quality.SeeThroughPlayers;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelExtractor.class)
public abstract class SeeThroughVisibilityMixin {
    @Shadow @Final private LevelRenderer levelRenderer;
    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void piggy$keepOccludedPlayers(Entity entity, Frustum frustum, double x, double y, double z,
                                             float partialTick, long fadeTime, CallbackInfoReturnable<Boolean> cir) {
        // Retain normal distance and camera-frustum limits; only bypass the terrain section occlusion check.
        if (SeeThroughPlayers.includes(entity))
            cir.setReturnValue(levelRenderer.entityRenderDispatcher().shouldRender(entity, frustum, x, y, z, partialTick));
    }
}
