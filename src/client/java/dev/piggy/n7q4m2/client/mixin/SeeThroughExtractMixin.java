package dev.piggy.n7q4m2.client.mixin;

import dev.piggy.n7q4m2.client.quality.SeeThroughPlayers;
import dev.piggy.n7q4m2.client.quality.SeeThroughState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class SeeThroughExtractMixin {
    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void piggy$extractChoice(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        ((SeeThroughState)cir.getReturnValue()).piggy$seeThrough(SeeThroughPlayers.includes(entity));
    }
}
