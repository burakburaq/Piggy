package dev.piggy.n7q4m2.test;

import dev.piggy.n7q4m2.client.quality.QualityClient;
import dev.piggy.n7q4m2.client.quality.hud.PerformanceMetrics;
import dev.piggy.n7q4m2.client.quality.hud.SimpleDebug;
import dev.piggy.n7q4m2.quality.metrics.UsageColor;
import dev.piggy.n7q4m2.quality.metrics.WorldColors;
import net.minecraft.core.registries.Registries;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.chat.Component;

/** Only the revised F3 performance/position fields and small-screen layout. */
public final class PerformanceHudGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void color(Component line, String text, int from, int expected) {
        int start = line.getString().indexOf(text, from);
        check(start >= 0, "Missing styled text: " + text);
        var colors = new java.util.ArrayList<Integer>();
        line.getVisualOrderText().accept((i, style, ch) -> {
            colors.add(style.getColor() == null ? 0xFFFFFF : style.getColor().getValue()); return true;
        });
        for (int i = start; i < start + text.length(); i++)
            check(colors.get(i) == expected, "Incorrect color for " + text + " in " + line.getString());
    }
    @Override public void runTest(ClientGameTestContext ctx) {
        boolean old = QualityClient.config.simpleDebug;
        try (var world = ctx.worldBuilder().create()) {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("tp @a -1.25 -60 17.75 135 0");
            ctx.waitFor(c -> c.player != null && c.gui.screen() == null, 200);
            ctx.runOnClient(c -> {
                QualityClient.config.simpleDebug = true; c.debugEntries.setOverlayVisible(true);
                c.options.reducedDebugInfo().set(false);
            });
            ctx.waitFor(c -> PerformanceMetrics.snapshot().cpu() >= 0 && PerformanceMetrics.snapshot().ramTotal() > 0
                    && PerformanceMetrics.snapshot().lowFps() > 0, 400);
            ctx.runOnClient(c -> {
                var state = PerformanceMetrics.snapshot();
                check(state.cpu() <= 100 && state.gpu() >= 0 && state.gpu() <= 100, "Invalid CPU/GPU percentages");
                check(state.ramUsed() >= 0 && state.ramUsed() <= state.ramTotal(), "Invalid physical memory sample");
                var styled = SimpleDebug.lines();
                var lines = styled.stream().map(Component::getString).toList();
                int perf = lines.indexOf("Performance");
                check(lines.get(perf + 1).matches("FPS: \\d+, \\d+ \\(1% low\\)"), "FPS/1% low format incorrect: " + lines);
                check(lines.get(perf + 2).startsWith("CPU: ") && lines.get(perf + 3).startsWith("Graphics: ")
                        && lines.get(perf + 4).startsWith("Mem: ") && lines.get(perf + 5).startsWith("SysMem: "), "Performance rows out of order");
                UsageColor[] palettes = UsageColor.values();
                for (int i = 0; i < 4; i++) {
                    var row = styled.get(perf + 2 + i);
                    String text = row.getString(); int start = text.indexOf(": ") + 2, end = text.indexOf('%');
                    String value = text.substring(start, end);
                    color(row, text.substring(0, start), 0, 0xFFFFFF);
                    color(row, value, start, palettes[i].at(value.equals("?") ? -1 : Integer.parseInt(value)));
                    color(row, "%", end, 0xFFFFFF);
                    if (text.contains(", ")) color(row, text.substring(end + 1), end + 1, 0xFFFFFF);
                }
                String heapLimit = String.format(java.util.Locale.ROOT, "/%.1fgb", Runtime.getRuntime().maxMemory() / 1073741824.0);
                check(lines.get(perf + 4).endsWith(heapLimit), "Mem must use the game's heap limit");
                String systemLimit = String.format(java.util.Locale.ROOT, "/%.1fgb", state.ramTotal() / 1073741824.0);
                check(lines.get(perf + 5).endsWith(systemLimit), "SysMem must use physical system RAM");
                int pos = lines.indexOf("Position");
                check(lines.get(pos + 2).equals("Facing: X- Z- (north)"), "Facing not immediately below XYZ");
                check(lines.contains("Chunk: -1 -4 1, 14 4 1 (in)") && lines.stream().noneMatch(s -> s.startsWith("Chunk in:")), "Chunk fields were not combined correctly");
                var chunk = styled.get(pos + 3);
                color(chunk, "Chunk: ", 0, 0xFFFFFF);
                color(chunk, "-1", 0, 0xFF0000); color(chunk, "-4", 0, 0x00FF00); color(chunk, "1", 12, 0x0000FF);
                color(chunk, "14", 15, 0xFF0000); color(chunk, "4", 19, 0x00FF00); color(chunk, "1", 21, 0x0000FF);
                var converted = styled.get(pos + 4);
                color(converted, "-1", 0, 0xFF0000); color(converted, "-60", 0, 0x00FF00); color(converted, "2", 0, 0x0000FF);
                for (var biome : c.level.registryAccess().lookupOrThrow(Registries.BIOME).keySet()) {
                    if (biome.getNamespace().equals("minecraft"))
                        check(WorldColors.biome("minecraft", biome.getPath()) != 0xFFFFFF, "Missing vanilla biome palette: " + biome);
                }
                var biomeId = c.level.getBiome(c.player.blockPosition()).unwrapKey().orElseThrow().identifier();
                color(styled.get(pos + 5), biomeId.getPath(), 7, WorldColors.biome(biomeId.getNamespace(), biomeId.getPath()));
                color(styled.get(pos + 6), "overworld", 0, 0x9BEDA6);
                check(WorldColors.dimension("minecraft", "the_nether") == 0xFF9292
                        && WorldColors.dimension("minecraft", "the_end") == 0xD7A5FF, "Dimension palette mismatch");
                check(WorldColors.biome("example", "plains") == 0xFFFFFF && WorldColors.dimension("example", "overworld") == 0xFFFFFF, "Modded IDs confused with vanilla");
                check(lines.contains("Dimension: overworld") && lines.stream().noneMatch(s -> (s.startsWith("Biome:") || s.startsWith("Dimension:")) && s.contains("minecraft:")), "Vanilla namespace still displayed");
                c.options.reducedDebugInfo().set(true);
                check(SimpleDebug.lines().stream().noneMatch(row -> row.getString().equals("Position")), "Reduced F3 reused full position rows");
                c.options.reducedDebugInfo().set(false);
                check(SimpleDebug.lines().stream().anyMatch(row -> row.getString().equals("Position")), "Full F3 did not restore position rows");
                org.slf4j.LoggerFactory.getLogger("Piggy").info("F3_SAMPLES {}", state);
            });
            ctx.getInput().resizeWindow(1280, 720);
            ctx.runOnClient(c -> { c.options.guiScale().set(2); c.resizeGui(); });
            ctx.waitTicks(2); ctx.takeScreenshot("f3-performance-640");
            ctx.getInput().resizeWindow(640, 480); ctx.waitTicks(2);
            ctx.runOnClient(c -> {
                var body = SimpleDebug.bounds(320);
                check(body.inside(320, 240), "F3 body outside the minimum screen: " + body);
                for (var extra : SimpleDebug.extraBounds(320)) check(extra.inside(320, 240) && !extra.overlaps(body), "F3 sections overlap");
            });
            ctx.takeScreenshot("f3-performance-320");
            ctx.runOnClient(c -> org.slf4j.LoggerFactory.getLogger("Piggy").info("PERFORMANCE_HUD_PASS"));
        } finally {
            ctx.runOnClient(c -> { QualityClient.config.simpleDebug = old; c.debugEntries.setOverlayVisible(false); });
        }
    }
}
