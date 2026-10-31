package dev.piggy.n7q4m2.test;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;

/** Diagnostic CPU/allocation sample inside a live client; never an FPS claim or timing assertion. */
final class PerformanceProbe {
    private PerformanceProbe() {}
    static void sample(String name, Runnable action) {
        var bean = ManagementFactory.getThreadMXBean();
        var allocation = bean instanceof com.sun.management.ThreadMXBean b && b.isThreadAllocatedMemorySupported() ? b : null;
        if (allocation != null && !allocation.isThreadAllocatedMemoryEnabled()) allocation.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        for (int i = 0; i < 200; i++) action.run();
        long[] nanos = new long[3], bytes = new long[3];
        for (int batch = 0; batch < 3; batch++) {
            long allocated = allocation == null ? 0 : allocation.getThreadAllocatedBytes(thread);
            long start = System.nanoTime();
            for (int i = 0; i < 300; i++) action.run();
            nanos[batch] = (System.nanoTime() - start) / 300;
            bytes[batch] = allocation == null ? -1 : (allocation.getThreadAllocatedBytes(thread) - allocated) / 300;
        }
        Arrays.sort(nanos); Arrays.sort(bytes);
        System.out.printf(Locale.ROOT, "FLOWHOP_PERF %s: %d ns/op, %d bytes/op%n", name, nanos[1], bytes[1]);
    }
}
