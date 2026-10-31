package dev.piggy.n7q4m2.client.quality.hud.mixin;

import dev.piggy.n7q4m2.client.quality.QualityClient;
import dev.piggy.n7q4m2.client.quality.hud.SimpleDebug;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugScreenOverlay.class)
public abstract class SimpleDebugMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void piggy$simpleDebug(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (QualityClient.config.simpleDebug && client.player != null && client.level != null) {
            SimpleDebug.render(graphics);
            ci.cancel();
        }
    }
}
