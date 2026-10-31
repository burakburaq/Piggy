package dev.piggy.n7q4m2.quality.metrics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrameTimesTest {
    @Test void needsARealOnePercentSampleAndUsesTheSlowestFrameTimes() {
        var frames = new FrameTimes(); long now = 1; frames.frame(now);
        for (int i = 0; i < 99; i++) frames.frame(now += 10_000_000L);
        assertEquals(-1, frames.low(now));
        frames.frame(now += 50_000_000L);
        assertEquals(20, frames.low(now), .0001);
        for (int i = 0; i < 99; i++) frames.frame(now += 10_000_000L);
        frames.frame(now += 30_000_000L);
        assertEquals(25, frames.low(now), .0001); // Mean of 50ms and 30ms, not mean of their FPS values.
    }
    @Test void expiresOldFramesAndResetsAcrossMenus() {
        var frames = new FrameTimes(); long now = 1; frames.frame(now);
        for (int i = 0; i < 200; i++) frames.frame(now += 10_000_000L);
        assertEquals(100, frames.low(now), .0001);
        assertEquals(-1, frames.low(now + 11_000_000_000L));
        frames.reset(); assertEquals(-1, frames.low(now));
    }
    @Test void boundedHistoryWrapsWithoutKeepingOldSlowFrames() {
        var frames = new FrameTimes(); long now = 1; frames.frame(now);
        for (int i = 0; i < 100; i++) frames.frame(now += 100_000_000L);
        for (int i = 0; i < 9000; i++) frames.frame(now += 1_000_000L);
        assertEquals(1000, frames.low(now), .0001);
    }
    @Test void heapMatchesSortedReferenceAcrossWrappingAndExpiry() {
        var frames = new FrameTimes(); var samples = new java.util.ArrayDeque<long[]>();
        var random = new java.util.Random(42); long now = 1; frames.frame(now);
        for (int i = 0; i < 12000; i++) {
            long duration = 100_000L + random.nextInt(2_000_000);
            now += duration; frames.frame(now); samples.addLast(new long[]{now,duration});
            if (samples.size() > 8192) samples.removeFirst();
            if (i % 317 == 0 || i == 11999) {
                long end = now;
                var sorted = samples.stream().filter(s -> end - s[0] <= 10_000_000_000L).mapToLong(s -> s[1]).sorted().toArray();
                double expected = -1;
                if (sorted.length >= 100) {
                    int worst = (sorted.length + 99) / 100; double sum = 0;
                    for (int j = sorted.length - worst; j < sorted.length; j++) sum += sorted[j];
                    expected = 1_000_000_000.0 * worst / sum;
                }
                assertEquals(expected, frames.low(now), 1E-9, "Sample " + i);
            }
        }
    }
}
