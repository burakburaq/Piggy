package dev.piggy.n7q4m2.quality.metrics;

/** Rolling wall-clock tick rate; includes time between ticks, not just tick work. */
public final class TickWindow {
    private final long[] timestamps = new long[101];
    private int next;
    private int size;

    public void reset() { next = 0; size = 0; }
    public void sample(long nanos) {
        // A suspended process or an unusually long stop starts a fresh measurement.
        if (size > 0) {
            long last = timestamps[(next - 1 + timestamps.length) % timestamps.length];
            if (nanos <= last || nanos - last > 10_000_000_000L) reset();
        }
        timestamps[next] = nanos;
        next = (next + 1) % timestamps.length;
        size = Math.min(size + 1, timestamps.length);
    }
    public float tps(float targetRate) {
        if (size < 2 || !Float.isFinite(targetRate) || targetRate <= 0) return -1;
        int oldest = (next - size + timestamps.length) % timestamps.length;
        int newest = (next - 1 + timestamps.length) % timestamps.length;
        long elapsed = timestamps[newest] - timestamps[oldest];
        if (elapsed <= 0) return -1;
        return (float) Math.min(targetRate, (size - 1) * 1_000_000_000.0 / elapsed);
    }
}
