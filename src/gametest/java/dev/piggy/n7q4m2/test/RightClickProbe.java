package dev.piggy.n7q4m2.test;

import java.util.ArrayList;
import java.util.List;

public final class RightClickProbe {
    private static boolean recording, placementsOnly;
    private static final List<Integer> ticks = new ArrayList<>();
    public static void begin() { begin(false); }
    public static void begin(boolean placements) { ticks.clear(); recording = true; placementsOnly = placements; }
    public static void record(int tick) { if (recording && !placementsOnly) ticks.add(tick); }
    public static void placed(int tick) { if (recording && placementsOnly) ticks.add(tick); }
    public static List<Integer> finish() { recording = false; return List.copyOf(ticks); }
}
