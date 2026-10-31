package dev.piggy.n7q4m2.client.mixin;

import dev.piggy.n7q4m2.movement.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class ClientCollisionMixin {
    @Shadow protected abstract float getBlockSpeedFactor();
    @Unique private Vec3 piggy$preCollision;

    @Inject(method = "move", at = @At("HEAD"))
    private void piggy$beforeMove(MoverType type, Vec3 movement, CallbackInfo ci) {
        piggy$preCollision = null;
        if ((Object) this == Minecraft.getInstance().player && type == MoverType.SELF) {
            LocalPlayer player = (LocalPlayer) (Object) this;
            HopAccess access = (HopAccess) player;
            if (HopController.movementActive(player)
                    && !player.input.keyPresses.shift()
                    && (!player.onGround() || movement.y > 0)) {
                piggy$preCollision = movement;
                access.piggy$state().impactSpeedBps(movement.horizontalDistance() * 20);
            }
        }
    }

    @Inject(method = "move", at = @At("RETURN"))
    private void piggy$afterMove(MoverType type, Vec3 movement, CallbackInfo ci) {
        if (piggy$preCollision == null || (Object) this != Minecraft.getInstance().player) return;
        LocalPlayer player = (LocalPlayer) (Object) this;
        HopAccess access = (HopAccess) player;
        if (HopController.movementActive(player)) {
            // Do not restore blocked axes or undo honey/slime effects. Retain speed separately.
            if (player.horizontalCollision) {
                double factor = getBlockSpeedFactor();
                access.piggy$state().retain(piggy$preCollision.x * factor, piggy$preCollision.z * factor);
            } else {
                Vec3 velocity = player.getDeltaMovement();
                access.piggy$state().retain(velocity.x, velocity.z);
            }
        }
        piggy$preCollision = null;
    }
}
