package dev.piggy.n7q4m2.client.quality.hud.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.piggy.n7q4m2.client.quality.hud.PerformanceMetrics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class PerformanceMetricsMixin {
    @WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/debug/DebugScreenEntryList;isCurrentlyEnabled(Lnet/minecraft/resources/Identifier;)Z"))
    private boolean piggy$gpuTiming(DebugScreenEntryList entries, Identifier entry, Operation<Boolean> original) {
        return original.call(entries, entry) || entry.equals(DebugScreenEntries.GPU_UTILIZATION)
                && PerformanceMetrics.visible((Minecraft)(Object)this);
    }
    @Inject(method = "renderFrame", at = @At("TAIL"))
    private void piggy$sampleFrame(boolean render, CallbackInfo ci) {
        PerformanceMetrics.afterFrame((Minecraft)(Object)this);
    }
}
