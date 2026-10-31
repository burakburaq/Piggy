package dev.piggy.n7q4m2.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.piggy.n7q4m2.client.quality.SeeThroughState;
import dev.piggy.n7q4m2.client.quality.QualityClient;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class SeeThroughRenderMixin {
    @Shadow @Final private SubmitNodeStorage submitNodeStorage;
    @Shadow @Final private LevelRenderState levelRenderState;
    @Unique private final SubmitNodeStorage piggy$players = new SubmitNodeStorage();
    @Unique private boolean piggy$hasPlayers;

    @Inject(method = "render", at = @At("HEAD"))
    private void piggy$begin(CallbackInfo ci) { piggy$hasPlayers = false; }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private boolean piggy$preserveWorldDepth(boolean preserveDepth) {
        // Vanilla's always-on-top pass supplies a fresh depth target, preserving player self-occlusion.
        if (preserveDepth) return true;
        if (!QualityClient.config.seeThroughPlayers) return false;
        for (var state : levelRenderState.entityRenderStates)
            if (((SeeThroughState)state).piggy$seeThrough()) return true;
        return false;
    }

    @Redirect(method = "submitEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
    private void piggy$submitPlayer(EntityRenderDispatcher dispatcher, EntityRenderState state, CameraRenderState camera,
                                      double x, double y, double z, PoseStack poses, SubmitNodeCollector collector) {
        if (!((SeeThroughState)state).piggy$seeThrough()) {
            dispatcher.submit(state, camera, x, y, z, poses, collector); return;
        }
        dispatcher.submit(state, camera, x, y, z, poses, piggy$players);
        piggy$hasPlayers = true;
    }

    @Inject(method = "submitEntities", at = @At("RETURN"))
    private void piggy$transferPlayers(CallbackInfo ci) {
        if (!piggy$hasPlayers) return;
        // All players share a transfer, instead of visiting every phase after each player.
        for (var entry : piggy$players.getSubmitsPerOrder().int2ObjectEntrySet()) {
            var from = entry.getValue();
            var to = submitNodeStorage.order(entry.getIntKey());
            for (var phase : from.allPhases()) {
                var destination = phase == from.shadows ? to.shadows : phase == from.outline ? to.outline : to.alwaysOnTopGizmos;
                phase.sortInto((node, sorted) -> destination.submit(node));
            }
        }
        piggy$players.seeThrough().sortInto((node, sorted) -> submitNodeStorage.order(0).alwaysOnTopGizmos.submit(node));
    }

    @Inject(method = "frameHasAlwaysOnTopGizmos", at = @At("RETURN"), cancellable = true)
    private void piggy$enablePlayerPass(CallbackInfoReturnable<Boolean> cir) {
        if (piggy$hasPlayers) cir.setReturnValue(true);
    }
}
