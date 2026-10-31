package dev.piggy.n7q4m2.mixin;

import dev.piggy.n7q4m2.movement.HopAccess;
import dev.piggy.n7q4m2.movement.HopController;
import dev.piggy.n7q4m2.movement.HopPhysics;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements HopAccess {
    @Unique private Vec3 piggy$dimensionVelocity;
    public boolean piggy$dimensionTransfer() { return piggy$dimensionVelocity != null; }

    @Inject(method = "teleport", at = @At("HEAD"))
    private void piggy$beforeDimension(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        piggy$dimensionVelocity = transition.newLevel().dimension() != player.level().dimension()
                ? player.getKnownMovement() : null;
        if (piggy$dimensionVelocity != null && HopController.eligible(player)) {
            // knownMovement is a packet displacement, possibly a lag batch. Do
            // not promote it into an authoritative impulse above the old envelope.
            var bounded = HopPhysics.cap(new HopPhysics.Horizontal(piggy$dimensionVelocity.x, piggy$dimensionVelocity.z),
                    ((HopAccess) player).piggy$envelope().speed() * 20);
            piggy$dimensionVelocity = new Vec3(bounded.x(), piggy$dimensionVelocity.y, bounded.z());
        }
    }

    @Inject(method = "teleport", at = @At("RETURN"))
    private void piggy$afterDimension(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (piggy$dimensionVelocity != null) {
            player.setDeltaMovement(piggy$dimensionVelocity);
            ((HopAccess) player).piggy$envelope().restartClock();
            piggy$dimensionVelocity = null;
        }
    }

    @Inject(method = "restoreFrom", at = @At("RETURN"))
    private void piggy$respawnSettings(ServerPlayer oldPlayer, boolean alive, CallbackInfo ci) {
        HopAccess old = (HopAccess) oldPlayer, current = (HopAccess) this;
        current.piggy$settings(old.piggy$settings());
        current.piggy$enabled(old.piggy$enabled());
        current.piggy$state().reset();
        current.piggy$envelope().reset();
    }

    // ServerPlayer implements death separately and does not invoke Player.die.
    @Inject(method = "die", at = @At("RETURN"))
    private void piggy$serverDeath(DamageSource source, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        HopController.stop(player);
        player.setDeltaMovement(Vec3.ZERO);
        player.setKnownMovement(Vec3.ZERO);
        ((HopAccess) player).piggy$envelope().reset();
    }
}
