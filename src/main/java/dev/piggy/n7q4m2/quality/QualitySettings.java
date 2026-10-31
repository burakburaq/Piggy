package dev.piggy.n7q4m2.quality;

public record QualitySettings(boolean simpleDebug, boolean shulkerPreview, boolean autoRestock,
                              boolean placementAssist, boolean foodInfo, boolean pairedDoors,
                              boolean arrowCounter, boolean rocketCounter) {
    public static QualitySettings disabled() { return new QualitySettings(false, false, false, false, false, false, false, false); }
}
