package dev.piggy.n7q4m2.mixin;

import dev.piggy.n7q4m2.config.MovementSettings;
import dev.piggy.n7q4m2.movement.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin implements HopAccess {
    @Unique private final HopState piggy$state = new HopState();
    @Unique private final MovementEnvelope piggy$envelope = new MovementEnvelope();
    @Unique private MovementSettings piggy$settings = MovementSettings.defaults();
    @Unique private boolean piggy$enabled;

    public HopState piggy$state() { return piggy$state; }
    public MovementEnvelope piggy$envelope() { return piggy$envelope; }
    public MovementSettings piggy$settings() { return piggy$settings; }
    public void piggy$settings(MovementSettings settings) {
        if (piggy$settings.enabled() != settings.enabled()) piggy$state.reset();
        piggy$settings = settings;
    }
    public boolean piggy$enabled() { return piggy$enabled && piggy$settings.enabled(); }
    public void piggy$enabled(boolean enabled) { piggy$enabled = enabled; }

    @Inject(method = "tick", at = @At("HEAD"))
    private void piggy$tick(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        boolean wasActive = piggy$state.active();
        if (!piggy$enabled() || !HopController.eligible(player) || player.isShiftKeyDown()) {
            piggy$state.reset();
        } else if (player instanceof ServerPlayer serverPlayer) {
            boolean held = HopController.movementHeld(serverPlayer.getLastClientInput());
            if (serverPlayer.isChangingDimension()) piggy$state.pauseLandingTiming();
            else piggy$state.tick(player.onGround(), held, piggy$state.impactSpeedBps(), player.tickCount);
        }
        if (!player.isAlive()) player.setDeltaMovement(Vec3.ZERO);
        if (!player.isAlive()) piggy$envelope.reset();
        if (wasActive != piggy$state.active()) HopController.sync(player);
    }

    @WrapMethod(method = "actuallyHurt")
    private void piggy$actualFallDamage(ServerLevel level, DamageSource source, float amount, Operation<Void> original) {
        Player player = (Player) (Object) this;
        float before = player.getHealth() + player.getAbsorptionAmount();
        original.call(level, source, amount);
        // Observe effective damage after armor/absorption but before a totem heals
        // the player. An outer hurtServer comparison misses low-health totem use.
        if (source.is(DamageTypes.FALL) && amount > 0
                && player.getHealth() + player.getAbsorptionAmount() < before)
            HopController.stop(player);
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void piggy$death(DamageSource source, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        HopController.stop(player);
        player.setDeltaMovement(Vec3.ZERO);
    }

    @Inject(method = "die", at = @At("RETURN"))
    private void piggy$stopDeathImpulse(DamageSource source, CallbackInfo ci) {
        ((Player) (Object) this).setDeltaMovement(Vec3.ZERO);
    }
}
