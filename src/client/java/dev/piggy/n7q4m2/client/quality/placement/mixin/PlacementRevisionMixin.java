package dev.piggy.n7q4m2.client.quality.placement.mixin;

import dev.piggy.n7q4m2.client.quality.placement.PlacementAssist;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class PlacementRevisionMixin {
    @Inject(method = "setBlock", at = @At("RETURN"))
    private void piggy$blockChanged(BlockPos pos, BlockState state, int flags, int recursion,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) PlacementAssist.worldChanged();
    }
    @Inject(method = "setBlocksDirty", at = @At("HEAD"))
    private void piggy$blocksDirty(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        PlacementAssist.worldChanged();
    }
}
