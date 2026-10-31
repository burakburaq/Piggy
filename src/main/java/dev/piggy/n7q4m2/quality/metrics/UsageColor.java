package dev.piggy.n7q4m2.quality.metrics;

/** Percentage anchors for each performance field; intermediate values blend smoothly. */
public enum UsageColor {
    CPU(0, 25, 70, 80, 90, 97),
    GRAPHICS(0, 25, 70, 85, 90, 95),
    MEM(0, 40, 70, 80, 90, 95),
    SYS_MEM(0, 50, 70, 80, 90, 95);

    private static final int[] COLORS = {0x008000, 0x00FF00, 0xFFFF00, 0xFFA500, 0xFF0000, 0x800000};
    private final int[] stops;
    UsageColor(int... stops) { this.stops = stops; }

    public int at(double percent) {
        if (!Double.isFinite(percent) || percent < 0) return 0xFFFFFF;
        for (int i = 1; i < stops.length; i++) {
            if (percent > stops[i]) continue;
            double weight = (percent - stops[i - 1]) / (stops[i] - stops[i - 1]);
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
