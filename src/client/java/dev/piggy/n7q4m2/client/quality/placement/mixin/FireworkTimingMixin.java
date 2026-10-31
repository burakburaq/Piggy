package dev.piggy.n7q4m2.client.quality.placement.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.piggy.n7q4m2.client.quality.placement.UseTiming;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(FireworkRocketItem.class)
public abstract class FireworkTimingMixin {
    private static int piggy$duration(ItemStack stack) {
        var fireworks = stack.get(DataComponents.FIREWORKS);
        return fireworks == null ? 1 : fireworks.flightDuration();
    }
    @WrapMethod(method = "use")
    private InteractionResult piggy$boostTiming(Level level, Player player, InteractionHand hand, Operation<InteractionResult> original) {
        if (!level.isClientSide()) return original.call(level, player, hand);
        int duration = piggy$duration(player.getItemInHand(hand));
        var result = original.call(level, player, hand);
        if (result.consumesAction()) UseTiming.usedFirework(duration);
        return result;
    }
    @WrapMethod(method = "useOn")
    private InteractionResult piggy$launchTiming(UseOnContext context, Operation<InteractionResult> original) {
        if (!context.getLevel().isClientSide()) return original.call(context);
        int duration = piggy$duration(context.getItemInHand());
        var result = original.call(context);
        if (result.consumesAction()) UseTiming.usedFirework(duration);
        return result;
    }
}
