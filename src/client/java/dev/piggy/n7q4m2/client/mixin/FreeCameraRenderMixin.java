package dev.piggy.n7q4m2.client.mixin;

import dev.piggy.n7q4m2.client.quality.movement.FreeCamera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class FreeCameraRenderMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void piggy$hideDetachedHands(CallbackInfo ci) {
        if (FreeCamera.active()) ci.cancel();
    }
}
