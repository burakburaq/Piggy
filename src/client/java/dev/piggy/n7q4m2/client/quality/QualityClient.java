package dev.piggy.n7q4m2.client.quality;

import dev.piggy.n7q4m2.config.ConfigFiles;
import dev.piggy.n7q4m2.quality.QualityOfLife;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class QualityClient {
    public static QualityConfig config = new QualityConfig();
    private QualityClient() {}
    public static void init() { config = ConfigFiles.load("piggy-quality.json", QualityConfig.class, new QualityConfig()); }
    public static void sync() {
        if (Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(QualityOfLife.SettingsPayload.TYPE))
            ClientPlayNetworking.send(new QualityOfLife.SettingsPayload(config.settings()));
    }
    public static void save(QualityConfig updated) {
        config = updated;
        ConfigFiles.save("piggy-quality.json", config);
        sync();
    }
}
