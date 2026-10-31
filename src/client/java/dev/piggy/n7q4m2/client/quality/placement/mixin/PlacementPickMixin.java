package dev.piggy.n7q4m2.client.quality.placement.mixin;

import dev.piggy.n7q4m2.client.quality.placement.PlacementAssist;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class PlacementPickMixin {
    @Inject(method = "pick", at = @At("RETURN"))
    private void piggy$previewPlacement(float partialTick, CallbackInfo ci) {
        PlacementAssist.updatePreview(Minecraft.getInstance(), partialTick);
    }
}
