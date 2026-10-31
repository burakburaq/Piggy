package dev.piggy.n7q4m2.client.mixin;

import dev.piggy.n7q4m2.movement.HopController;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class ClientFovMixin {
    @Inject(method = "getFieldOfViewModifier", at = @At("HEAD"), cancellable = true)
    private void piggy$sprintFov(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer)(Object)this;
        if (!HopController.movementActive(player)) return;
        // Vanilla sprint multiplier is (1.3 + 1) / 2 = 1.15. Respect the user's FOV-effects slider.
        cir.setReturnValue(Mth.lerp(effectScale, 1, 1.15f));
    }
}
