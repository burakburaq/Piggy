package dev.piggy.n7q4m2;

import dev.piggy.n7q4m2.config.ConfigFiles;
import dev.piggy.n7q4m2.config.ServerPolicy;
import dev.piggy.n7q4m2.movement.HopAccess;
import dev.piggy.n7q4m2.movement.HopController;
import dev.piggy.n7q4m2.network.HopPayloads;
import dev.piggy.n7q4m2.quality.QualityOfLife;
import dev.piggy.n7q4m2.quality.inventory.RestockFeatures;
import dev.piggy.n7q4m2.quality.doors.PairedDoors;
import dev.piggy.n7q4m2.quality.metrics.Metrics;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Piggy implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("piggy");
    private static ServerPolicy policy = new ServerPolicy();
    public static ServerPolicy policy() { return policy; }

    @Override public void onInitialize() {
        QualityOfLife.init();
        RestockFeatures.initCommon();
        PairedDoors.initCommon();
        Metrics.initCommon();
        PayloadTypeRegistry.clientboundPlay().register(HopPayloads.Policy.TYPE, HopPayloads.Policy.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HopPayloads.State.TYPE, HopPayloads.State.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(HopPayloads.Settings.TYPE, HopPayloads.Settings.CODEC);
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            policy = ConfigFiles.load("piggy-server.json", ServerPolicy.class, new ServerPolicy());
            policy.sanitize();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (ServerPlayNetworking.canSend(handler.player, HopPayloads.Policy.TYPE))
                ServerPlayNetworking.send(handler.player, new HopPayloads.Policy(policy));
        });
        ServerPlayNetworking.registerGlobalReceiver(HopPayloads.Settings.TYPE, (payload, context) -> {
            HopAccess access = (HopAccess) context.player();
            access.piggy$settings(policy.constrain(payload.settings()));
            access.piggy$enabled(access.piggy$settings().enabled());
            if (!access.piggy$enabled()) access.piggy$state().reset();
            HopController.sync(context.player());
        });
    }
}
