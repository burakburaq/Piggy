package dev.piggy.n7q4m2.client.quality;

import dev.piggy.n7q4m2.quality.QualitySettings;

public final class QualityConfig {
    // A session action: never restart automatic movement on the next login.
    public transient boolean antiAfk;
    public boolean antiAfkJump = true;
    public boolean antiAfkCrouch = true;
    public boolean seeThroughPlayers;
    public boolean simpleDebug = true;
    public boolean shulkerPreview = true;
    public boolean autoRestock = true;
    public boolean bucketCycle = true;
    public boolean placementAssist = true;
    public boolean foodInfo = true;
    public boolean pairedDoors = true;
    public boolean arrowCounter = true;
    public boolean rocketCounter = true;

    public QualitySettings settings() {
        return new QualitySettings(simpleDebug, shulkerPreview, autoRestock, placementAssist,
                foodInfo, pairedDoors, arrowCounter, rocketCounter);
    }
    public QualityConfig copy() {
        QualityConfig c = new QualityConfig();
        c.antiAfk = antiAfk; c.antiAfkJump = antiAfkJump; c.antiAfkCrouch = antiAfkCrouch;
        c.seeThroughPlayers = seeThroughPlayers;
        c.simpleDebug = simpleDebug; c.shulkerPreview = shulkerPreview; c.autoRestock = autoRestock; c.bucketCycle = bucketCycle;
        c.placementAssist = placementAssist; c.foodInfo = foodInfo; c.pairedDoors = pairedDoors;
        c.arrowCounter = arrowCounter; c.rocketCounter = rocketCounter;
        return c;
    }
}
