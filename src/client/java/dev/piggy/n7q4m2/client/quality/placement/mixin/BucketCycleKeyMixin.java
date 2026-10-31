package dev.piggy.n7q4m2.client.quality.placement.mixin;

import dev.piggy.n7q4m2.client.quality.inventory.BucketCycle;
import dev.piggy.n7q4m2.client.quality.placement.PlantingUse;
import dev.piggy.n7q4m2.client.quality.placement.UseTiming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public abstract class BucketCycleKeyMixin {
    @Inject(method = "setDown", at = @At("HEAD"))
    private void piggy$releaseBucketLock(boolean down, CallbackInfo ci) {
        var key = (KeyMapping)(Object)this;
        if (!down && key.isDown()) {
            BucketCycle.keyReleased(key);
            PlantingUse.keyReleased(key);
            var mc = Minecraft.getInstance();
            if (mc.options != null && key == mc.options.keyUse) UseTiming.releasedUse();
        }
    }
}
