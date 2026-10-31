package dev.piggy.n7q4m2.movement;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class ServerMovementLimits {
    private ServerMovementLimits() {}

    public static boolean accept(ServerPlayer player, ServerboundMovePlayerPacket packet) {
        if (!packet.hasPosition()) return true;
        return accept(player, packet.getX(player.getX()), packet.getY(player.getY()), packet.getZ(player.getZ()));
    }

    /** Shared by ordinary movement and 26.3 teleport acknowledgements carrying a position. */
    public static boolean accept(ServerPlayer player, double x, double y, double z) {
        HopAccess access = (HopAccess) player;
        MovementEnvelope envelope = access.piggy$envelope();
        if (!HopController.eligible(player) || player.isSleeping()) {
            envelope.reset();
            return true; // Vanilla owns vehicles, swimming, climbing and flight.
        }
        double wishSpeed = HopPhysics.WALK_SPEED_BPS / 20
                * (player.getAttributeValue(Attributes.MOVEMENT_SPEED) / .1);
        // Vanilla sprint-jump adds .2 blocks/tick before custom air acceleration.
        double baseline = wishSpeed + (player.isSprinting() ? .2 : .02);
        var settings = access.piggy$settings();
        boolean bhop = HopController.movementActive(player);
        if (!bhop) {
            double friction = player.level().getBlockState(player.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction();
            // Conservative vanilla bound, including repeated low-ceiling sprint
            // jumps on ice. The floor's friction is read from the server world.
            double acceleration = player.getAttributeValue(Attributes.MOVEMENT_SPEED) * .216 / Math.pow(friction, 3);
            baseline = Math.max(baseline, (acceleration + (player.isSprinting() ? .2 : 0)) / (1 - .91 * friction));
        }
        boolean supported = !player.level().noCollision(player, player.getBoundingBox().move(0, -.05, 0));
        boolean air = !supported || y > player.getY();
        double gain = bhop ? Math.min(.08, settings.airAcceleration() * wishSpeed / 20) : .026;
        envelope.advance(player.tickCount, baseline, bhop ? settings.maxSpeedBps() / 20 : baseline, gain, air);
        return envelope.consume(Math.hypot(x - player.getX(),
                z - player.getZ()));
    }

    public static void outbound(ServerPlayer player, Packet<?> packet) {
        MovementEnvelope envelope = ((HopAccess) player).piggy$envelope();
        if (packet instanceof ClientboundSetEntityMotionPacket motion && motion.id() == player.getId()) {
            envelope.motion(motion.movement().horizontalDistance());
        } else if (packet instanceof ClientboundExplodePacket explosion) {
            explosion.playerKnockback().ifPresent(vector -> envelope.impulse(vector.horizontalDistance()));
        } else if (packet instanceof BundlePacket<?> bundle) {
            for (Packet<?> child : bundle.subPackets()) outbound(player, child);
        }
    }
}
