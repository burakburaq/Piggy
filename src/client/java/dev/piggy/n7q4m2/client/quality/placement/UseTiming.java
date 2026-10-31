package dev.piggy.n7q4m2.client.quality.placement;

/** Client-thread placement serial: distinguish actual placements from block/entity interactions in either hand. */
public final class UseTiming {
    private static long placements, waterUses, fireworkUses, useReleases;
    private static int fireworkInterval;
    private UseTiming() {}
    public static long placements() { return placements; }
    public static void placedBlock() { placements++; }
    public static long waterUses() { return waterUses; }
    public static void usedWaterBucket() { waterUses++; }
    public static long fireworkUses() { return fireworkUses; }
    public static int fireworkInterval() { return fireworkInterval; }
    public static void usedFirework(int duration) {
        fireworkInterval = 20 + 10 * Math.clamp(duration, 1, 3);
        fireworkUses++;
    }
    public static long useReleases() { return useReleases; }
    public static void releasedUse() { useReleases++; }
}
