package dev.piggy.n7q4m2.quality.metrics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PingColorTest {
    @Test void matchesAllRequestedAnchorsAndClampsOutsideThem() {
        assertEquals(0x008000, PingColor.at(0));
        assertEquals(0x00FF00, PingColor.at(100));
        assertEquals(0xFFFF00, PingColor.at(300));
        assertEquals(0xFFA500, PingColor.at(600));
        assertEquals(0xFF0000, PingColor.at(1000));
        assertEquals(0xFF0000, PingColor.at(2500));
        assertEquals(0x008000, PingColor.at(-1));
    }
    @Test void interpolatesBetweenStops() {
        assertEquals(0x80FF00, PingColor.at(200));
        assertEquals(0xFFD200, PingColor.at(450));
    }
}
