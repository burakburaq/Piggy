package dev.piggy.n7q4m2.quality.inventory.mixin;

import dev.piggy.n7q4m2.quality.inventory.RestockFeatures;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
public abstract class RestockItemStackMixin {
    @WrapMethod(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V")
    private void piggy$restockBrokenTool(int damage, ServerLevel level, ServerPlayer player, Consumer<Item> onBreak, Operation<Void> operation) {
        ItemStack stack = (ItemStack) (Object) this;
        RestockFeatures.Destination destination = RestockFeatures.captureHeld(player, stack);
        if (destination == null) {
            operation.call(damage, level, player, onBreak);
            return;
        }
        ItemStack original = stack.copy();
        boolean[] actuallyBroke = { false };
        Consumer<Item> observedBreak = item -> {
            actuallyBroke[0] = true;
            onBreak.accept(item);
        };
        operation.call(damage, level, player, observedBreak);
        if (actuallyBroke[0]) RestockFeatures.toolBroken(player, destination, original);
    }
}
