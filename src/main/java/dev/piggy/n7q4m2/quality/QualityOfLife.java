package dev.piggy.n7q4m2.quality;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Independent of the movement toggle: client preferences for server-backed helpers. */
public final class QualityOfLife {
    private static final Map<UUID, QualitySettings> SETTINGS = new ConcurrentHashMap<>();
    private QualityOfLife() {}
    public static QualitySettings settings(ServerPlayer player) {
        return SETTINGS.getOrDefault(player.getUUID(), QualitySettings.disabled());
    }
    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(SettingsPayload.TYPE, SettingsPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SettingsPayload.TYPE, (payload, context) ->
                SETTINGS.put(context.player().getUUID(), payload.settings()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SETTINGS.remove(handler.player.getUUID()));
    }
    public record SettingsPayload(QualitySettings settings) implements CustomPacketPayload {
        public static final Type<SettingsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("piggy", "quality_settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SettingsPayload> CODEC = new StreamCodec<>() {
            @Override public SettingsPayload decode(RegistryFriendlyByteBuf b) {
                return new SettingsPayload(new QualitySettings(b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(),
                        b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean()));
            }
            @Override public void encode(RegistryFriendlyByteBuf b, SettingsPayload payload) {
                var s = payload.settings;
                b.writeBoolean(s.simpleDebug()); b.writeBoolean(s.shulkerPreview()); b.writeBoolean(s.autoRestock());
                b.writeBoolean(s.placementAssist()); b.writeBoolean(s.foodInfo()); b.writeBoolean(s.pairedDoors());
                b.writeBoolean(s.arrowCounter()); b.writeBoolean(s.rocketCounter());
            }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
