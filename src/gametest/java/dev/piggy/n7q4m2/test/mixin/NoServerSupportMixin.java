package dev.piggy.n7q4m2.test.mixin;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import io.netty.channel.ChannelFutureListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test-only fixture: simulate a connection that never receives Piggy server messages. */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class NoServerSupportMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), cancellable = true)
    private void piggy$withoutServerMessages(Packet<?> packet, ChannelFutureListener callback, CallbackInfo ci) {
        if (Boolean.getBoolean("piggy.test.noServerSupport")
                && packet instanceof ClientboundCustomPayloadPacket custom
                && custom.payload().type().id().getNamespace().equals("piggy")) ci.cancel();
    }
}
