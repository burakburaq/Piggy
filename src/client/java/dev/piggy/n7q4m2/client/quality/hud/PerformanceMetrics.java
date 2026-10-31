package dev.piggy.n7q4m2.client.quality.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sun.management.OperatingSystemMXBean;
import dev.piggy.n7q4m2.client.quality.QualityClient;
import dev.piggy.n7q4m2.quality.metrics.FrameTimes;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.NVXGPUMemoryInfo;
import java.lang.management.ManagementFactory;
import java.util.concurrent.CompletableFuture;

public final class PerformanceMetrics {
    public record Snapshot(double lowFps, double cpu, double gpu, long ramUsed, long ramTotal, long vramUsed, long vramTotal) {}
    private record SystemSample(double cpu, long used, long total) {}
    private static final FrameTimes FRAMES = new FrameTimes();
    private static volatile Snapshot snapshot = new Snapshot(-1, -1, -1, -1, -1, -1, -1);
    private static volatile SystemSample system = new SystemSample(-1, -1, -1);
    private static volatile boolean samplingSystem;
    private static long nextSample;
    private static boolean warmedSystem;
    private PerformanceMetrics() {}
    public static Snapshot snapshot() { return snapshot; }
    public static boolean visible(Minecraft c) {
        return QualityClient.config.simpleDebug && c.player != null && c.level != null && c.gui.screen() == null
                && !c.gui.hud.isHidden() && c.debugEntries.isOverlayVisible();
    }
    public static void afterFrame(Minecraft client) {
        if (!QualityClient.config.simpleDebug || client.player == null || client.level == null || client.gui.screen() != null || client.isPaused()) {
            FRAMES.reset(); nextSample = 0; return;
        }
        long now = System.nanoTime(); FRAMES.frame(now);
        if (!visible(client)) { nextSample = 0; return; }
        if (now < nextSample) return;
        nextSample = now + 1_000_000_000L;
        long vramUsed = -1, vramTotal = -1;
        var info = RenderSystem.getDevice().getDeviceInfo();
        if (info.backendName().equalsIgnoreCase("OpenGL")) {
            try {
                if (GL.getCapabilities().GL_NVX_gpu_memory_info) {
                    long total = GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX) * 1024L;
                    long free = GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX) * 1024L;
                    if (total > 0 && free >= 0 && free <= total) { vramTotal = total; vramUsed = total - free; }
                }
            } catch (IllegalStateException | UnsupportedOperationException ignored) { /* Unsupported context: report unknown. */ }
        }
        double gpu = client.getGpuUtilization();
        if (!Double.isFinite(gpu) || gpu < 0) gpu = -1;
        else gpu = Math.min(100, gpu);
        var os = system;
        snapshot = new Snapshot(FRAMES.low(now), os.cpu(), gpu, os.used(), os.total(), vramUsed, vramTotal);
        if (!samplingSystem) {
            samplingSystem = true;
            CompletableFuture.runAsync(() -> {
                try {
                    if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean bean) {
                        double cpu = bean.getCpuLoad(); long total = bean.getTotalMemorySize(), free = bean.getFreeMemorySize();
                        cpu = warmedSystem && Double.isFinite(cpu) && cpu >= 0 ? Math.min(100, cpu * 100) : -1;
                        warmedSystem = true;
                        system = total > 0 && free >= 0 && free <= total
                                ? new SystemSample(cpu, total - free, total) : new SystemSample(cpu, -1, -1);
                    }
                } catch (RuntimeException | LinkageError ignored) {
                    system = new SystemSample(-1, -1, -1);
                } finally { samplingSystem = false; }
            });
        }
    }
}
