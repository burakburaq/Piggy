package dev.piggy.n7q4m2.network;

import dev.piggy.n7q4m2.config.MovementSettings;
import dev.piggy.n7q4m2.config.ServerPolicy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class HopPayloads {
    private HopPayloads() {}
    private static <T> StreamCodec<RegistryFriendlyByteBuf, T> codec(
            BiConsumer<RegistryFriendlyByteBuf, T> writer, Function<RegistryFriendlyByteBuf, T> reader) {
        return new StreamCodec<>() {
            public T decode(RegistryFriendlyByteBuf buf) { return reader.apply(buf); }
            public void encode(RegistryFriendlyByteBuf buf, T value) { writer.accept(buf, value); }
        };
    }

    public record Policy(ServerPolicy policy) implements CustomPacketPayload {
        public static final Type<Policy> TYPE = new Type<>(Identifier.fromNamespaceAndPath("piggy", "policy"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Policy> CODEC = codec((b, v) -> {
            ServerPolicy p = v.policy;
            b.writeBoolean(p.enabled);
            b.writeDouble(p.maxAirAcceleration); b.writeDouble(p.maxGroundAcceleration);
            b.writeDouble(p.minGroundFriction); b.writeDouble(p.maxGroundFriction);
            b.writeDouble(p.minAirFriction); b.writeDouble(p.maxAirFriction);
            b.writeDouble(p.minSpeedCapBps); b.writeDouble(p.maxSpeedCapBps);
        }, b -> {
            ServerPolicy p = new ServerPolicy();
            p.enabled = b.readBoolean();
            p.maxAirAcceleration = b.readDouble(); p.maxGroundAcceleration = b.readDouble();
            p.minGroundFriction = b.readDouble(); p.maxGroundFriction = b.readDouble();
            p.minAirFriction = b.readDouble(); p.maxAirFriction = b.readDouble();
            p.minSpeedCapBps = b.readDouble(); p.maxSpeedCapBps = b.readDouble();
            p.sanitize();
            return new Policy(p);
        });
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Settings(MovementSettings settings) implements CustomPacketPayload {
        public static final Type<Settings> TYPE = new Type<>(Identifier.fromNamespaceAndPath("piggy", "settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Settings> CODEC = codec((b, v) -> {
            MovementSettings s = v.settings;
            b.writeDouble(s.airAcceleration()); b.writeDouble(s.groundAcceleration());
            b.writeDouble(s.groundFriction()); b.writeDouble(s.airFriction()); b.writeDouble(s.maxSpeedBps());
            b.writeBoolean(s.autoJump()); b.writeBoolean(s.enabled());
        }, b -> new Settings(new MovementSettings(b.readDouble(), b.readDouble(), b.readDouble(),
                b.readDouble(), b.readDouble(), b.readBoolean(), b.readBoolean())));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record State(boolean active) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Identifier.fromNamespaceAndPath("piggy", "state_v3"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = codec(
                (b, v) -> b.writeBoolean(v.active), b -> new State(b.readBoolean()));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
