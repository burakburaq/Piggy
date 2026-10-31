package dev.piggy.n7q4m2.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.piggy.n7q4m2.config.ConfigFiles;
import dev.piggy.n7q4m2.config.ServerPolicy;
import dev.piggy.n7q4m2.movement.HopAccess;
import dev.piggy.n7q4m2.network.HopPayloads;
import dev.piggy.n7q4m2.client.quality.QualityClient;
import dev.piggy.n7q4m2.client.quality.movement.AntiAfk;
import dev.piggy.n7q4m2.client.quality.movement.FreeCamera;
import dev.piggy.n7q4m2.client.quality.inventory.InventoryFeatures;
import dev.piggy.n7q4m2.client.quality.inventory.BucketCycle;
import dev.piggy.n7q4m2.client.quality.placement.PlacementAssist;
import dev.piggy.n7q4m2.client.quality.hud.HudFeatures;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;


public final class PiggyClient implements ClientModInitializer {
    public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("piggy", "settings"));
    public static ClientConfig config = new ClientConfig();
    public static ServerPolicy policy = new ServerPolicy();
    public static boolean supported;

    @Override public void onInitializeClient() {
        QualityClient.init();
        AntiAfk.init();
        FreeCamera.init();
        InventoryFeatures.initClient();
        BucketCycle.init();
        PlacementAssist.initClient();
        HudFeatures.initClient();
        config = ConfigFiles.load("piggy-client.json", ClientConfig.class, new ClientConfig());
        config.sanitize();
        KeyMapping settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.piggy.settings",
                InputConstants.Type.KEYBOARD, InputConstants.KEY_O,
                KEY_CATEGORY));
        ClientPlayConnectionEvents.INIT.register((handler, client) -> clearServerPolicy());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> applySettings());
        ClientPlayNetworking.registerGlobalReceiver(HopPayloads.Policy.TYPE, (payload, context) -> {
            policy = payload.policy(); supported = true; applySettings();
        });
        ClientPlayNetworking.registerGlobalReceiver(HopPayloads.State.TYPE, (payload, context) -> {
            if (supported && context.player() != null) {
                HopAccess access = (HopAccess) context.player();
                access.piggy$state().applyRemote(access.piggy$enabled() && payload.active());
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (client.player != null) {
                HopAccess access = (HopAccess) client.player;
                access.piggy$enabled(false);
                access.piggy$state().reset();
            }
            clearServerPolicy();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (settingsKey.consumeClick()) {
                if (client.gui.screen() == null) client.gui.setScreen(new PiggyConfigScreen(null));
            }
            if (client.player != null) {
                HopAccess access = (HopAccess) client.player;
                access.piggy$enabled(client.getConnection() != null && (!supported || policy.enabled));
                access.piggy$settings(config.movement(supported ? policy : null));
            }
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("piggy", "speed"), SpeedHud::render);
    }

    private static void clearServerPolicy() {
        supported = false;
        policy = new ServerPolicy();
    }

    public static void applySettings() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            HopAccess access = (HopAccess) client.player;
            access.piggy$enabled(client.getConnection() != null && (!supported || policy.enabled));
            access.piggy$settings(config.movement(supported ? policy : null));
        }
        if (supported && ClientPlayNetworking.canSend(HopPayloads.Settings.TYPE))
            ClientPlayNetworking.send(new HopPayloads.Settings(config.movement()));
        QualityClient.sync();
    }

    public static void save(ClientConfig updated) {
        config = updated; config.sanitize();
        ConfigFiles.save("piggy-client.json", config);
        applySettings();
    }
}
