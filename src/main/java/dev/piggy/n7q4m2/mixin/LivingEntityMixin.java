package dev.piggy.n7q4m2.mixin;

import dev.piggy.n7q4m2.movement.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow protected abstract int calculateFallDamage(double distance, float multiplier);
    @Shadow private SoundEvent getFallDamageSound(int damage) { throw new AssertionError(); }
    @Shadow protected abstract void playBlockFallSound();

    @Inject(method = "jumpFromGround", at = @At("HEAD"))
    private void piggy$jump(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            HopAccess access = (HopAccess) player;
            if (!access.piggy$enabled() || !HopController.eligible(player) || player.isShiftKeyDown()) return;
            boolean previous = access.piggy$state().active();
            access.piggy$state().jump(player.getY(), player.tickCount, HopController.movementHeld(player.getLastClientInput()));
            if (previous != access.piggy$state().active()) HopController.sync(player);
        }
    }

    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void piggy$fallDamage(double distance, float multiplier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Player player) || player.level().isClientSide()) return;
        HopAccess access = (HopAccess) player;
        if (!access.piggy$enabled() || !access.piggy$state().active()) return;
        int normalDamage = calculateFallDamage(distance, multiplier);
        float damage = HopPhysics.fallDamage(normalDamage, access.piggy$state().impactSpeedBps(), true);
        if (damage > 0) {
            player.playSound(getFallDamageSound(normalDamage), 1, 1);
            playBlockFallSound();
            player.hurt(source, damage);
        }
        cir.setReturnValue(damage > 0);
    }
}
