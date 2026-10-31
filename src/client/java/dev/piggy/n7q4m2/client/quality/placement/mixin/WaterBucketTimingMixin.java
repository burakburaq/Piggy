package dev.piggy.n7q4m2.client.quality.placement.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.piggy.n7q4m2.client.quality.placement.UseTiming;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BucketItem.class)
public abstract class WaterBucketTimingMixin {
    @WrapMethod(method = "use")
    private InteractionResult piggy$pourWater(Level level, Player player, InteractionHand hand, Operation<InteractionResult> original) {
        if (level.isClientSide() && player.getItemInHand(hand).is(Items.WATER_BUCKET)) UseTiming.usedWaterBucket();
        return original.call(level, player, hand);
    }
    @WrapOperation(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemUtils;createFilledResult(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack piggy$collectWater(ItemStack empty, Player player, ItemStack filled, Operation<ItemStack> original) {
        // Inspect the picked-up fluid before creative-mode inventory handling can retain the empty bucket.
        if (player.level().isClientSide() && filled.is(Items.WATER_BUCKET)) UseTiming.usedWaterBucket();
        return original.call(empty, player, filled);
    }
}
