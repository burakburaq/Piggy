package dev.piggy.n7q4m2.quality.metrics;

public final class PingColor {
    private static final int[] STOPS = {0, 100, 300, 600, 1000};
    private static final int[] COLORS = {0x008000, 0x00FF00, 0xFFFF00, 0xFFA500, 0xFF0000};
    private PingColor() {}
    public static int at(int milliseconds) {
        if (milliseconds <= 0) return COLORS[0];
        for (int i = 1; i < STOPS.length; i++) if (milliseconds <= STOPS[i]) {
            double weight = (double)(milliseconds - STOPS[i - 1]) / (STOPS[i] - STOPS[i - 1]);
            int color = 0;
            for (int shift = 16; shift >= 0; shift -= 8) {
                int from = COLORS[i - 1] >> shift & 255, to = COLORS[i] >> shift & 255;
                color |= (int)Math.round(from + (to - from) * weight) << shift;
            }
            return color;
        }
        return COLORS[COLORS.length - 1];
    }
}
