package dev.piggy.n7q4m2.client.quality.placement.mixin;

import dev.piggy.n7q4m2.client.quality.placement.PlantingUse;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class PlantingUseMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void piggy$plantOnly(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                   CallbackInfoReturnable<InteractionResult> cir) {
        if (PlantingUse.blocksFailedPlanting(player, hand, hit)) cir.setReturnValue(InteractionResult.PASS);
    }
    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void piggy$doNotEat(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (PlantingUse.blocksEating(player, hand)) cir.setReturnValue(InteractionResult.PASS);
    }
}
