package dev.piggy.n7q4m2.quality.metrics;

import dev.piggy.n7q4m2.quality.QualityOfLife;
import dev.piggy.n7q4m2.mixin.FoodDataAccessor;
import net.minecraft.world.level.gamerules.GameRules;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class Metrics {
    private static final TickWindow WINDOW = new TickWindow();
    private static int ticks;
    private Metrics() {}

    public static void initCommon() {
        PayloadTypeRegistry.clientboundPlay().register(Snapshot.TYPE, Snapshot.CODEC);
        ServerLifecycleEvents.SERVER_STARTING.register(server -> { WINDOW.reset(); ticks = 0; });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { WINDOW.reset(); ticks = 0; });
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (server.isPaused()) { WINDOW.reset(); return; }
            WINDOW.sample(System.nanoTime());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.isPaused() || ++ticks % 5 != 0) return;
            float tps = server.tickRateManager().isFrozen() ? 0 : WINDOW.tps(server.tickRateManager().tickrate());
            for (var player : server.getPlayerList().getPlayers()) {
                var preferences = QualityOfLife.settings(player);
                if ((!preferences.simpleDebug() && !preferences.foodInfo())
                        || !ServerPlayNetworking.canSend(player, Snapshot.TYPE)) continue;
                ServerPlayNetworking.send(player, new Snapshot(tps, player.getFoodData().getSaturationLevel(),
                        player.level().dimension().identifier(), ((FoodDataAccessor)player.getFoodData()).piggy$exhaustion(),
                        player.level().getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION)));
            }
        });
    }

    public record Snapshot(float tps, float saturation, Identifier dimension, float exhaustion, boolean naturalRegeneration) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(Identifier.fromNamespaceAndPath("piggy", "hud_metrics_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = new StreamCodec<>() {
            @Override public Snapshot decode(RegistryFriendlyByteBuf buffer) {
                return new Snapshot(buffer.readFloat(), buffer.readFloat(), buffer.readIdentifier(), buffer.readFloat(), buffer.readBoolean());
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, Snapshot snapshot) {
                buffer.writeFloat(snapshot.tps()); buffer.writeFloat(snapshot.saturation());
                buffer.writeIdentifier(snapshot.dimension());
                buffer.writeFloat(snapshot.exhaustion()); buffer.writeBoolean(snapshot.naturalRegeneration());
            }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
