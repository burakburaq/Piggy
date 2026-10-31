package dev.piggy.n7q4m2.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.piggy.n7q4m2.client.quality.movement.AntiAfk;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class AntiAfkKeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void piggy$physicalKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action == InputConstants.PRESS || action == InputConstants.REPEAT) AntiAfk.keyPressed(event);
    }
}
