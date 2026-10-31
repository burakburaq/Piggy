package dev.piggy.n7q4m2.quality.metrics;

/** Bounded ten-second frame history; 1% low is the reciprocal mean of the slowest 1% frame times. */
public final class FrameTimes {
    private static final long WINDOW_NS = 10_000_000_000L;
    private final long[] times = new long[8192], durations = new long[8192];
    // At most ceil(8192 / 100) slow samples. Reused; low() allocates nothing.
    private final long[] slowest = new long[82];
    private int next, count;
    private long previous;
    public void reset() { next = count = 0; previous = 0; }
    public void frame(long now) {
        if (previous > 0 && now > previous) {
            times[next] = now; durations[next] = now - previous;
            next = (next + 1) % times.length; count = Math.min(count + 1, times.length);
        }
        previous = now;
    }
    public double low(long now) {
        int size = 0;
        for (int i = 0; i < count; i++) if (times[i] <= now && now - times[i] <= WINDOW_NS) size++;
        if (size < 100) return -1;
        int worst = (size + 99) / 100, selected = 0;
        for (int i = 0; i < count; i++) {
            if (times[i] > now || now - times[i] > WINDOW_NS) continue;
            long duration = durations[i];
            if (selected < worst) {
                int index = selected++;
                while (index > 0) {
                    int parent = (index - 1) / 2;
                    if (slowest[parent] <= duration) break;
                    slowest[index] = slowest[parent]; index = parent;
                }
                slowest[index] = duration;
            } else if (duration > slowest[0]) {
                int index = 0;
                while (index * 2 + 1 < selected) {
                    int child = index * 2 + 1;
                    if (child + 1 < selected && slowest[child + 1] < slowest[child]) child++;
                    if (slowest[child] >= duration) break;
                    slowest[index] = slowest[child]; index = child;
                }
                slowest[index] = duration;
            }
        }
        double sum = 0;
        for (int i = 0; i < worst; i++) sum += slowest[i];
        return 1_000_000_000.0 * worst / sum;
    }
}
