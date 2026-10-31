package dev.piggy.n7q4m2.mixin;

import dev.piggy.n7q4m2.movement.ServerMovementLimits;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import io.netty.channel.ChannelFutureListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerImpulseMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"))
    private void piggy$authorizedImpulse(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
        if ((Object) this instanceof ServerGamePacketListenerImpl connection) {
            var player = connection.player;
            var server = player.level().getServer();
            if (server.isSameThread()) ServerMovementLimits.outbound(player, packet);
            else server.execute(() -> ServerMovementLimits.outbound(player, packet));
        }
    }
}
