package dev.piggy.n7q4m2.client.mixin;
import dev.piggy.n7q4m2.client.quality.movement.FreeCamera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Minecraft.class)
public abstract class FreeCameraInteractionMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void piggy$noAttack(CallbackInfoReturnable<Boolean> cir) { if (FreeCamera.active()) cir.setReturnValue(false); }
    @Inject(method = {"startUseItem", "pickBlockOrEntity"}, at = @At("HEAD"), cancellable = true)
    private void piggy$noUse(CallbackInfo ci) { if (FreeCamera.active()) ci.cancel(); }
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void piggy$noContinue(boolean held, CallbackInfo ci) { if (FreeCamera.active()) ci.cancel(); }
}
