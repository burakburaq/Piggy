package dev.piggy.n7q4m2.client.mixin;
import dev.piggy.n7q4m2.client.quality.movement.FreeCamera;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LocalPlayer.class)
public abstract class FreeCameraPlayerMixin {
    @Inject(method = "isControlledCamera", at = @At("HEAD"), cancellable = true)
    private void piggy$keepBodyNetworking(CallbackInfoReturnable<Boolean> cir) {
        if (FreeCamera.active()) cir.setReturnValue(true);
    }
}
