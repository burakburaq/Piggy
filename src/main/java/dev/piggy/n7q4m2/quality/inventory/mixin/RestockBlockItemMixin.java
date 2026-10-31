package dev.piggy.n7q4m2.quality.inventory.mixin;

import dev.piggy.n7q4m2.quality.inventory.RestockFeatures;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockItem.class)
public abstract class RestockBlockItemMixin {
    @WrapMethod(method = "place")
    private InteractionResult piggy$restockPlacedBlock(BlockPlaceContext context, Operation<InteractionResult> operation) {
        ServerPlayer player = context.getPlayer() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        RestockFeatures.Destination destination = player == null ? null
                : RestockFeatures.capture(player, context.getHand(), context.getItemInHand());
        ItemStack original = destination == null ? ItemStack.EMPTY : context.getItemInHand().copy();
        InteractionResult result = operation.call(context);
        if (destination != null && result.consumesAction()) RestockFeatures.blockConsumed(player, destination, original);
        return result;
    }
}
