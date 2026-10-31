package dev.piggy.n7q4m2.client.quality.placement.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.piggy.n7q4m2.client.quality.placement.UseTiming;
import dev.piggy.n7q4m2.client.quality.placement.PlantingUse;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockItem.class)
public abstract class BlockPlacementTimingMixin {
    @WrapOperation(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BlockItem;placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean piggy$recordPlacement(BlockItem item, BlockPlaceContext context, BlockState state, Operation<Boolean> original) {
        boolean placed = original.call(item, context, state);
        if (placed && context.getLevel().isClientSide()) {
            UseTiming.placedBlock();
            PlantingUse.planted(context);
        }
        return placed;
    }
    @WrapOperation(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/Item;use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult piggy$skipEatingFallback(BlockItem item, Level level, Player player, InteractionHand hand,
                                                        Operation<InteractionResult> original) {
        return PlantingUse.blocksEating(player, hand) ? InteractionResult.PASS : original.call(item, level, player, hand);
    }
}
