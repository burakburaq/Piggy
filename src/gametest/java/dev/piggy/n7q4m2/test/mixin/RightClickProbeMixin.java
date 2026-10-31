package dev.piggy.n7q4m2.test.mixin;

import dev.piggy.n7q4m2.test.RightClickProbe;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class RightClickProbeMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void piggy$recordUse(CallbackInfo ci) {
        var player = ((Minecraft)(Object)this).player;
        if (player != null) RightClickProbe.record(player.tickCount);
    }
}
