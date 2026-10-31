package dev.piggy.n7q4m2.quality.metrics;

import java.util.Locale;

/** Locale-independent HUD numbers without constructing a Formatter for each field/tick. */
public final class HudNumbers {
    private HudNumbers() {}
    public static String oneDecimal(double value) {
        if (!Double.isFinite(value) || Math.abs(value) > 1E16) return String.format(Locale.ROOT, "%.1f", value);
        long tenths = Math.round(Math.abs(value) * 10);
        String sign = Double.doubleToRawLongBits(value) < 0 ? "-" : "";
        return sign + tenths / 10 + "." + tenths % 10;
    }
}
