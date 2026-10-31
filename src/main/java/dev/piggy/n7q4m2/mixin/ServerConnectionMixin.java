package dev.piggy.n7q4m2.mixin;

import dev.piggy.n7q4m2.movement.HopController;
import dev.piggy.n7q4m2.movement.HopAccess;
import dev.piggy.n7q4m2.movement.ServerMovementLimits;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Set;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Unique;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerConnectionMixin {
    @Shadow public ServerPlayer player;
    @Shadow private double firstGoodX;
    @Shadow private double firstGoodZ;
    @Unique private boolean piggy$handlingMovement;
    @Unique private double piggy$validatedHorizontalSquared;
    @WrapMethod(method = "handlePlayerPositionChange")
    private void piggy$validatedPosition(double x, double y, double z, float yaw, float pitch,
            boolean onGround, boolean horizontalCollision, Operation<Void> original) {
        // Both ordinary movement and teleport ACKs reach here after vanilla finite-value/thread checks.
        // ACKs must spend the same server-clock credit rather than introduce another movement path.
        boolean previous = piggy$handlingMovement;
        piggy$handlingMovement = true;
        piggy$validatedHorizontalSquared = 0;
        try {
            if (!ServerMovementLimits.accept(player, x, y, z)) {
                ((HopAccess) player).piggy$envelope().forfeit();
                player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                return;
            }
            if (HopController.eligible(player)) {
                double dx = x - firstGoodX, dz = z - firstGoodZ;
                piggy$validatedHorizontalSquared = dx * dx + dz * dz;
            }
            original.call(x, y, z, yaw, pitch, onGround, horizontalCollision);
        } finally {
            piggy$handlingMovement = previous;
            piggy$validatedHorizontalSquared = 0;
        }
    }

    @ModifyExpressionValue(method = "handlePlayerPositionChange", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;lengthSqr()D", ordinal = 0))
    private double piggy$accountForValidatedHorizontal(double vanillaVelocitySquared) {
        // Horizontal path was already checked against server-time credit. Keep
        // vanilla's vertical speed and collision checks, including on lag bursts.
        return vanillaVelocitySquared + piggy$validatedHorizontalSquared;
    }

    @Inject(method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V", at = @At("HEAD"))
    private void piggy$teleport(PositionMoveRotation position, Set<Relative> relatives, CallbackInfo ci) {
        if (!((HopAccess) player).piggy$dimensionTransfer()) {
            // Both our rejection and vanilla vertical/collision corrections are
            // client-triggerable. ACKing any of them must not restart the clock.
            if (piggy$handlingMovement) ((HopAccess) player).piggy$envelope().forfeit();
            else ((HopAccess) player).piggy$envelope().reset();
            HopController.stop(player);
            player.setDeltaMovement(Vec3.ZERO);
        }
    }

    @Inject(method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V", at = @At("RETURN"))
    private void piggy$resetTeleportVelocity(PositionMoveRotation position, Set<Relative> relatives, CallbackInfo ci) {
        if (!((HopAccess) player).piggy$dimensionTransfer()) {
            player.setDeltaMovement(Vec3.ZERO);
            player.setKnownMovement(Vec3.ZERO);
        }
    }
}
