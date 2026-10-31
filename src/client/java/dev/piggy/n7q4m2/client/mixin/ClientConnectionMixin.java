package dev.piggy.n7q4m2.client.mixin;

import dev.piggy.n7q4m2.client.PiggyClient;
import dev.piggy.n7q4m2.client.quality.movement.FreeCamera;
import dev.piggy.n7q4m2.movement.HopAccess;
import dev.piggy.n7q4m2.movement.HopState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientConnectionMixin {
    @Unique private boolean piggy$dimensionTransition;
    @Unique private HopState piggy$oldState;
    @Unique private Vec3 piggy$dimensionVelocity;

    @Inject(method = "handleRespawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setCameraEntity(Lnet/minecraft/world/entity/Entity;)V", ordinal = 0))
    private void piggy$beforeRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        piggy$dimensionTransition = player != null && !player.isDeadOrDying()
                && packet.commonPlayerSpawnInfo().dimension() != player.level().dimension();
        piggy$oldState = piggy$dimensionTransition ? ((HopAccess) player).piggy$state() : null;
        piggy$dimensionVelocity = piggy$dimensionTransition ? player.getDeltaMovement() : null;
    }

    @Inject(method = "handleRespawn", at = @At("RETURN"))
    private void piggy$afterRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player != null && piggy$oldState != null) {
            HopState state = ((HopAccess) player).piggy$state();
            state.applyRemote(piggy$oldState.active());
            var retained = piggy$oldState.retainedMovement();
            state.retain(retained.x(), retained.z());
            state.impactSpeedBps(piggy$oldState.impactSpeedBps());
            player.setDeltaMovement(piggy$dimensionVelocity);
        }
        PiggyClient.applySettings();
    }

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
    private void piggy$stopFreeCamera(ClientboundPlayerPositionPacket packet, CallbackInfo ci) { FreeCamera.stop(); }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void piggy$positionPacket(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        if (piggy$dimensionTransition) {
            player.setDeltaMovement(piggy$dimensionVelocity);
            piggy$dimensionTransition = false;
            piggy$dimensionVelocity = null;
            piggy$oldState = null;
        } else {
            ((HopAccess) player).piggy$state().reset();
            player.setDeltaMovement(Vec3.ZERO);
        }
    }
}
