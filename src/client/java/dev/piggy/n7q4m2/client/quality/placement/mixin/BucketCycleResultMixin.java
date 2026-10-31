package dev.piggy.n7q4m2.client.quality.placement.mixin;

import dev.piggy.n7q4m2.client.quality.inventory.BucketCycle;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class BucketCycleResultMixin {
    @Inject(method = {"useItem", "useItemOn", "interact"}, at = @At("RETURN"))
    private void piggy$bucketResult(CallbackInfoReturnable<InteractionResult> cir) {
        BucketCycle.result(cir.getReturnValue());
    }
}
