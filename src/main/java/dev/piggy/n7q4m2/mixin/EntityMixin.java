package dev.piggy.n7q4m2.mixin;

import dev.piggy.n7q4m2.movement.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "move", at = @At("HEAD"))
    private void piggy$impactSpeed(MoverType type, Vec3 movement, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && type == MoverType.PLAYER) {
            HopAccess access = (HopAccess) player;
            // Sample pre-collision movement; never derive damage from the retained wall speed.
            double speed = movement.horizontalDistance() * 20;
            if (Double.isFinite(speed)) access.piggy$state().impactSpeedBps(speed);
        }
    }

    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z", at = @At("RETURN"))
    private void piggy$mount(Entity vehicle, boolean force, boolean notify, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && cir.getReturnValue()) HopController.stop(player);
    }
}
