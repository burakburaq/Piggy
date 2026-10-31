package dev.piggy.n7q4m2.client.quality;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;

public final class SeeThroughPlayers {
    private SeeThroughPlayers() {}
    public static boolean includes(Entity entity) {
        return QualityClient.config.seeThroughPlayers && entity instanceof AbstractClientPlayer
                && entity != Minecraft.getInstance().player && !entity.isInvisible();
    }
}
