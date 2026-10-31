package dev.piggy.n7q4m2.quality.inventory.mixin;

import dev.piggy.n7q4m2.quality.inventory.RestockFeatures;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
public abstract class RestockTotemMixin {
    @WrapMethod(method = "checkTotemDeathProtection")
    private boolean piggy$restockConsumedTotem(DamageSource source, Operation<Boolean> operation) {
        ServerPlayer player = (Object) this instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        RestockFeatures.Destination destination = null;
        if (player != null) {
            // Vanilla examines MAIN_HAND first, and any death-protection item wins.
            for (InteractionHand hand : InteractionHand.values()) {
                var stack = player.getItemInHand(hand);
                if (stack.has(DataComponents.DEATH_PROTECTION)) {
                    if (stack.is(Items.TOTEM_OF_UNDYING)) destination = RestockFeatures.capture(player, hand, stack);
                    break;
                }
            }
        }
        boolean protectedFromDeath = operation.call(source);
        if (protectedFromDeath && destination != null) RestockFeatures.totemConsumed(player, destination);
        return protectedFromDeath;
    }
}
