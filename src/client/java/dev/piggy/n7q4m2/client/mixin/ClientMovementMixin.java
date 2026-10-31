package dev.piggy.n7q4m2.client.mixin;

import dev.piggy.n7q4m2.client.PiggyClient;
import dev.piggy.n7q4m2.config.MovementSettings;
import dev.piggy.n7q4m2.movement.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class ClientMovementMixin {
    @Shadow private int noJumpDelay;
    @Unique private boolean piggy$jumpHeld;
    @Unique private boolean piggy$jumpRising;
    @Unique private Vec3 piggy$beforeJump;
    @Unique private boolean piggy$physicsApplied;
    @Unique private boolean piggy$hasYaw;
    @Unique private float piggy$lastYaw;

    @Unique private LocalPlayer piggy$local() {
        return (Object) this == Minecraft.getInstance().player ? (LocalPlayer) (Object) this : null;
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void piggy$input(CallbackInfo ci) {
        LocalPlayer player = piggy$local();
        if (player == null) return;
        HopAccess access = (HopAccess) player;
        boolean jump = player.input.keyPresses.jump() && Minecraft.getInstance().gui.screen() == null;
        piggy$jumpRising = jump && !piggy$jumpHeld;
        piggy$jumpHeld = jump;
        if (!access.piggy$enabled() || !HopController.eligible(player)) return;
        boolean held = HopController.movementHeld(player.input.keyPresses);
        if (Minecraft.getInstance().gui.screen() instanceof LevelLoadingScreen) access.piggy$state().pauseLandingTiming();
        else access.piggy$state().tick(player.onGround(), held,
                    Math.max(player.getDeltaMovement().horizontalDistance(), access.piggy$state().retainedSpeed()) * 20, player.tickCount);
        if (jump && (access.piggy$settings().autoJump() || piggy$jumpRising)
                && !player.input.keyPresses.shift()) noJumpDelay = 0;
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void piggy$jump(CallbackInfo ci) {
        LocalPlayer player = piggy$local();
        if (player == null) return;
        HopAccess access = (HopAccess) player;
        if (!access.piggy$enabled() || !HopController.eligible(player) || player.input.keyPresses.shift()) return;
        if (Minecraft.getInstance().gui.screen() != null || !access.piggy$settings().autoJump() && !piggy$jumpRising) {
            ci.cancel(); return;
        }
        access.piggy$state().jump(player.getY(), player.tickCount, HopController.movementHeld(player.input.keyPresses));
        piggy$beforeJump = access.piggy$state().active() ? player.getDeltaMovement() : null;
    }

    @Inject(method = "jumpFromGround", at = @At("RETURN"))
    private void piggy$keepJumpMomentum(CallbackInfo ci) {
        LocalPlayer player = piggy$local();
        if (player != null && piggy$beforeJump != null) {
            player.setDeltaMovement(piggy$beforeJump.x, player.getDeltaMovement().y, piggy$beforeJump.z);
            piggy$beforeJump = null;
        }
    }

    @Redirect(method = "handleRelativeFrictionAndCalculateMovement", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V"))
    private void piggy$accelerate(LivingEntity entity, float vanillaAcceleration, Vec3 input) {
        LocalPlayer player = piggy$local();
        piggy$physicsApplied = false;
        float yawDelta = 0;
        if (player != null) {
            yawDelta = piggy$hasYaw ? Mth.wrapDegrees(player.getYRot() - piggy$lastYaw) : 0;
            piggy$lastYaw = player.getYRot();
            piggy$hasYaw = true;
        }
        if (player == null || !HopController.movementActive(player)
                || !HopController.eligible(player)
                || player.input.keyPresses.shift()
                || player.onGround() && player.getDeltaMovement().y <= 0) {
            entity.moveRelative(vanillaAcceleration, input); return;
        }
        piggy$physicsApplied = true;
        HopAccess access = (HopAccess) player;
        MovementSettings settings = access.piggy$settings();
        Vec3 current = player.getDeltaMovement();
        var velocity = new HopPhysics.Horizontal(current.x, current.z);
        double inputLength = Math.min(1, input.horizontalDistance());
        boolean held = HopController.movementHeld(player.input.keyPresses) && Minecraft.getInstance().gui.screen() == null;
        if (player.horizontalCollision && access.piggy$state().retainedSpeed() > velocity.speed()) {
            velocity = access.piggy$state().retainedMovement();
        }
        if (!held) {
            velocity = HopPhysics.friction(velocity, settings.airFriction());
        }
        if (held && inputLength > 1.0e-6) {
            velocity = HopPhysics.turnWithView(velocity, yawDelta);
            double yaw = Math.toRadians(player.getYRot()), cos = Math.cos(yaw), sin = Math.sin(yaw);
            double wx = input.x * cos - input.z * sin, wz = input.z * cos + input.x * sin;
            // Recover retained wall momentum in the newly requested direction.
            if (player.horizontalCollision && access.piggy$state().retainedSpeed() > current.horizontalDistance()) {
                double length = Math.hypot(wx, wz), retained = access.piggy$state().retainedSpeed();
                velocity = new HopPhysics.Horizontal(wx / length * retained, wz / length * retained);
            }
            double wishSpeed = HopPhysics.WALK_SPEED_BPS / 20 * (player.getSpeed() / .1) * inputLength;
            velocity = HopPhysics.accelerate(velocity, wx, wz, wishSpeed,
                    .08 * inputLength, settings.airAcceleration());
        }
        // External impulses can exceed the cap; do not erase them, only cap new bhop acceleration.
        velocity = HopPhysics.cap(velocity, Math.max(settings.maxSpeedBps(), current.horizontalDistance() * 20));
        player.setDeltaMovement(velocity.x(), current.y, velocity.z());
    }

    @Redirect(method = "travelInAir", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V"))
    private void piggy$removeVanillaDrag(LivingEntity entity, double x, double y, double z) {
        LocalPlayer player = piggy$local();
        if (player != null && piggy$physicsApplied && HopController.movementActive(player)
                && HopController.eligible(player)) {
            Vec3 movement = player.getDeltaMovement();
            entity.setDeltaMovement(movement.x, y, movement.z);
        } else entity.setDeltaMovement(x, y, z);
    }
}
