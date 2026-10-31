package dev.piggy.n7q4m2.quality.metrics;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudNumbersTest {
    @Test void retainsHudRoundingSignsAndCoordinateRange() {
        for (double v : new double[]{0, -0.0, -.01, .05, -.05, .15, 1.25, -1.25, 3.14159, 19.96, -60, 29999999.94, -29999999.96})
            assertEquals(String.format(Locale.ROOT, "%.1f", v), HudNumbers.oneDecimal(v), "Value " + v);
    }
    @Test void keepsDecimalPointUnderTurkishLocale() {
        Locale previous = Locale.getDefault();
        try { Locale.setDefault(Locale.forLanguageTag("tr-TR")); assertEquals("5.5", HudNumbers.oneDecimal(5.5)); }
        finally { Locale.setDefault(previous); }
    }
}
