package dev.piggy.n7q4m2.client.quality.hud;

import dev.piggy.n7q4m2.quality.metrics.Metrics;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class ClientMetrics {
    private static float tps = -1;
    private static float saturation = -1;
    private static float exhaustion;
    private static boolean naturalRegeneration;
    private static Identifier dimension;
    private static long receivedNanos;
    private ClientMetrics() {}

    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(Metrics.Snapshot.TYPE, (snapshot, context) -> {
            if (context.client().level == null
                    || !context.client().level.dimension().identifier().equals(snapshot.dimension())) return;
            tps = Float.isFinite(snapshot.tps()) && snapshot.tps() >= 0 ? snapshot.tps() : -1;
            saturation = Float.isFinite(snapshot.saturation())
                    ? Math.max(0, Math.min(20, snapshot.saturation())) : -1;
            dimension = snapshot.dimension();
            exhaustion = Float.isFinite(snapshot.exhaustion()) ? Math.max(0, Math.min(40, snapshot.exhaustion())) : 0;
            naturalRegeneration = snapshot.naturalRegeneration();
            receivedNanos = System.nanoTime();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || (dimension != null && !dimension.equals(client.level.dimension().identifier()))) reset();
        });
    }
    private static void reset() { tps = -1; saturation = -1; exhaustion = 0; naturalRegeneration = false; dimension = null; receivedNanos = 0; }
    public static boolean available() {
        Minecraft client = Minecraft.getInstance();
        return dimension != null && client.level != null && dimension.equals(client.level.dimension().identifier())
                && (client.isPaused() || System.nanoTime() - receivedNanos < 10_000_000_000L);
    }
    public static float tps() { return available() ? tps : -1; }
    public static float saturation() { return available() ? saturation : -1; }
    public static float exhaustion() { return available() ? exhaustion : 0; }
    public static boolean naturalRegeneration() { return available() && naturalRegeneration; }
}
