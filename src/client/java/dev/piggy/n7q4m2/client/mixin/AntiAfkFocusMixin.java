package dev.piggy.n7q4m2.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.piggy.n7q4m2.client.quality.QualityClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class AntiAfkFocusMixin {
    // Leave the saved preference and explicit ESC/menu handling untouched.
    @ModifyExpressionValue(method = "pauseIfInactive", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Options;pauseOnLostFocus:Z"))
    private boolean piggy$keepAntiAfkRunning(boolean pauseOnLostFocus) {
        return pauseOnLostFocus && !QualityClient.config.antiAfk;
    }
}
