package dev.piggy.n7q4m2.test.mixin;

import dev.piggy.n7q4m2.test.RightClickProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class PlacementProbeMixin {
    @Inject(method = "placeBlock", at = @At("RETURN"))
    private void piggy$placed(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && context.getLevel().isClientSide())
            RightClickProbe.placed(Minecraft.getInstance().player.tickCount);
    }
}
