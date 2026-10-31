package dev.piggy.n7q4m2.quality.metrics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UsageColorTest {
    @Test void usesEveryRequestedAnchorForEachMetric() {
        int[][] stops = {{0,25,70,80,90,97}, {0,25,70,85,90,95}, {0,40,70,80,90,95}, {0,50,70,80,90,95}};
        int[] colors = {0x008000,0x00FF00,0xFFFF00,0xFFA500,0xFF0000,0x800000};
        for (var palette : UsageColor.values()) {
            for (int i = 0; i < colors.length; i++)
                assertEquals(colors[i], palette.at(stops[palette.ordinal()][i]), palette + " anchor " + i);
            assertEquals(0x800000, palette.at(100));
            assertEquals(0xFFFFFF, palette.at(-1));
            assertEquals(0xFFFFFF, palette.at(Double.NaN));
        }
    }
    @Test void blendsIntermediateValues() {
        assertEquals(0x80FF00, UsageColor.CPU.at(47.5));
        assertEquals(0xFFD200, UsageColor.GRAPHICS.at(77.5));
        assertEquals(0xC00000, UsageColor.MEM.at(92.5));
        assertEquals(0x00C000, UsageColor.SYS_MEM.at(25));
    }
}
