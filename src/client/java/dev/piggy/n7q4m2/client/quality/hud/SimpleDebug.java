package dev.piggy.n7q4m2.client.quality.hud;

import dev.piggy.n7q4m2.client.quality.QualityClient;
import dev.piggy.n7q4m2.quality.metrics.HudLayout;
import dev.piggy.n7q4m2.quality.metrics.PingColor;
import dev.piggy.n7q4m2.quality.metrics.UsageColor;
import dev.piggy.n7q4m2.quality.metrics.WorldColors;
import dev.piggy.n7q4m2.quality.metrics.HudNumbers;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.locale.Language;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.List;

public final class SimpleDebug {
    public static final float FONT_SCALE = 12f / 9f;
    private static final int RED = 0xFF9292, GREEN = 0x9BEDA6, BLUE = 0x8FCAFF, PURPLE = 0xD7A5FF;
    private static Object cachedPlayer, cachedLevel, cachedLanguage;
    private static int cachedTick, cachedFps;
    private static boolean cachedReduced;
    private static List<Component> cachedLines = List.of();
    private static Layout cachedLayout;
    private static final Component[] ROWS = new Component[21], MEASURED = new Component[21];
    private static final String[] KEYS = new String[21];
    private static final int[] COLORS = new int[21], WIDTHS = new int[21];
    private static Object measuredLanguage;
    private static long revision, publishedRevision = -1;
    private static void initRows() {
        if (ROWS[0] != null) return;
        java.util.Arrays.fill(ROWS, Component.empty());
        ROWS[0] = colored("Version", RED);
        ROWS[1] = Component.literal("- Minecraft " + SharedConstants.getCurrentVersion().name());
        ROWS[3] = colored("Performance", GREEN); ROWS[10] = colored("Position", BLUE); ROWS[18] = colored("Network", PURPLE);
    }
    private record Layout(List<Component> lines, int width, int height, boolean effects, boolean compact, Object language,
                          int bodyEnd, HudLayout.Rectangle body, List<HudLayout.Rectangle> extra) {}
    private SimpleDebug() {}
    public static void invalidate() {
        cachedPlayer = null; cachedLevel = null; cachedLanguage = null;
        cachedLines = List.of(); cachedLayout = null; publishedRevision = -1;
        // Drop component/language caches too when F3 closes or a world disconnects.
        java.util.Arrays.fill(ROWS, null); java.util.Arrays.fill(MEASURED, null); java.util.Arrays.fill(KEYS, null);
        measuredLanguage = null;
    }
    public static List<Component> lines() {
        Minecraft client = Minecraft.getInstance();
        if (!QualityClient.config.simpleDebug || client.player == null || client.level == null
                || client.gui.hud.isHidden() || client.gui.screen() != null || !client.debugEntries.isOverlayVisible()) {
            if (cachedPlayer != null) invalidate();
            return List.of();
        }
        var player = client.player;
        initRows();
        boolean reduced = client.showOnlyReducedInfo();
        Object language = Language.getInstance();
        if (cachedPlayer == player && cachedLevel == client.level && cachedTick == player.tickCount
                && cachedFps == client.getFps() && cachedReduced == reduced && cachedLanguage == language) return cachedLines;
        boolean reducedChanged = cachedReduced != reduced;
        cachedPlayer = player; cachedLevel = client.level; cachedTick = player.tickCount;
        cachedFps = client.getFps(); cachedReduced = reduced; cachedLanguage = language;
        var performance = PerformanceMetrics.snapshot();
        text(4, "FPS: " + client.getFps() + ", " + number(performance.lowFps()) + " (1% low)");
        percentRow(5, "CPU: ", performance.cpu(), UsageColor.CPU, "");
        percentRow(6, "Graphics: ", performance.gpu(), UsageColor.GRAPHICS,
                ", " + memory(performance.vramUsed(), performance.vramTotal()));
        var runtime = Runtime.getRuntime();
        long heapUsed = runtime.totalMemory() - runtime.freeMemory(), heapTotal = runtime.maxMemory();
        percentRow(7, "Mem: ", heapUsed * 100.0 / heapTotal, UsageColor.MEM, ", " + memory(heapUsed, heapTotal));
        double ramPercent = performance.ramTotal() > 0 ? performance.ramUsed() * 100.0 / performance.ramTotal() : -1;
        percentRow(8, "SysMem: ", ramPercent, UsageColor.SYS_MEM, ", " + memory(performance.ramUsed(), performance.ramTotal()));
        if (!reduced) {
            String x = HudNumbers.oneDecimal(player.getX()), y = HudNumbers.oneDecimal(player.getY()), z = HudNumbers.oneDecimal(player.getZ());
            if (changed(11, "X " + x + " / Y " + y + " / Z " + z, 0))
                ROWS[11] = colored("X", 0xFF0000).append(white(" " + x + " / "))
                        .append(colored("Y", 0x00FF00)).append(white(" " + y + " / "))
                        .append(colored("Z", 0x0000FF)).append(white(" " + z));
            double yaw = Math.toRadians(player.getYRot());
            String xSign = -Math.sin(yaw) < -1.0E-7 ? "-" : "+", zSign = Math.cos(yaw) < -1.0E-7 ? "-" : "+";
            String direction = player.getDirection().getName();
            if (changed(12, "Facing: X" + xSign + " Z" + zSign + " (" + direction + ")", 0))
                ROWS[12] = white("Facing: ").append(colored("X", 0xFF0000)).append(white(xSign + " "))
                        .append(colored("Z", 0x0000FF)).append(white(zSign + " (" + direction + ")"));
            var pos = player.blockPosition();
            String chunk = "Chunk: " + (pos.getX() >> 4) + " " + (pos.getY() >> 4) + " " + (pos.getZ() >> 4)
                    + ", " + (pos.getX() & 15) + " " + (pos.getY() & 15) + " " + (pos.getZ() & 15) + " (in)";
            if (changed(13, chunk, 0)) ROWS[13] = white("Chunk: ").append(coordinates(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4))
                    .append(white(", ")).append(coordinates(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15)).append(white(" (in)"));
            boolean nether = client.level.dimension().equals(Level.NETHER);
            double factor = nether ? 8 : 1.0 / 8;
            long convertedX = (long)Math.floor(player.getX() * factor), convertedZ = (long)Math.floor(player.getZ() * factor);
            String label = nether ? "Overworld: " : "Nether: ";
            if (changed(14, label + convertedX + " " + pos.getY() + " " + convertedZ, 0))
                ROWS[14] = white(label).append(coordinates(convertedX, pos.getY(), convertedZ));
            var biome = client.level.getBiome(pos).unwrapKey();
            String biomeName = biome.map(key -> displayId(key.identifier())).orElse("?");
            int biomeColor = biome.map(key -> WorldColors.biome(key.identifier().getNamespace(), key.identifier().getPath())).orElse(0xFFFFFF);
            if (changed(15, biomeName, biomeColor)) ROWS[15] = white("Biome: ").append(colored(biomeName, biomeColor));
            var dimension = client.level.dimension().identifier();
            String dimensionName = displayId(dimension);
            int dimensionColor = WorldColors.dimension(dimension.getNamespace(), dimension.getPath());
            if (changed(16, dimensionName, dimensionColor)) ROWS[16] = white("Dimension: ").append(colored(dimensionName, dimensionColor));
        }
        var info = client.getConnection() == null ? null : client.getConnection().getPlayerInfo(player.getUUID());
        int latency = info == null ? -1 : info.getLatency(), pingColor = latency < 0 ? 0xFFFFFF : PingColor.at(latency);
        String ping = latency < 0 ? "?" : Integer.toString(latency);
        if (changed(19, ping, pingColor)) ROWS[19] = white("Ping: ").append(colored(ping, pingColor)).append(white(" ms"));
        float tps = ClientMetrics.tps(); text(20, "TPS: " + (tps < 0 ? "?" : HudNumbers.oneDecimal(tps)));
        if (publishedRevision != revision || reducedChanged || cachedLines.isEmpty()) {
            var lines = new ArrayList<Component>(21);
            for (int i = 0; i < ROWS.length; i++) if (!reduced || i < 10 || i >= 18) lines.add(ROWS[i]);
            cachedLines = List.copyOf(lines); publishedRevision = revision;
        }
        return cachedLines;
    }
    private static boolean changed(int row, String key, int color) {
        if (key.equals(KEYS[row]) && color == COLORS[row]) return false;
        KEYS[row] = key; COLORS[row] = color; revision++; return true;
    }
    private static void text(int row, String value) {
        if (changed(row, value, 0xFFFFFF)) ROWS[row] = white(value);
    }
    private static void percentRow(int row, String label, double value, UsageColor palette, String tail) {
        String number = number(value);
        int color = palette.at(number.equals("?") ? -1 : Math.round(value));
        if (changed(row, label + number + "%" + tail, color))
            ROWS[row] = white(label).append(colored(number, color)).append(white("%" + tail));
    }
    private static String displayId(net.minecraft.resources.Identifier id) {
        return id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
    }
    private static String number(double value) { return value >= 0 && Double.isFinite(value) ? Long.toString(Math.round(value)) : "?"; }
    private static MutableComponent coordinates(long x, long y, long z) {
        return colored(Long.toString(x), 0xFF0000).append(white(" "))
                .append(colored(Long.toString(y), 0x00FF00)).append(white(" "))
                .append(colored(Long.toString(z), 0x0000FF));
    }
    private static String memory(long used, long total) {
        return used >= 0 && total > 0 ? HudNumbers.oneDecimal(used / 1073741824.0) + "/" + HudNumbers.oneDecimal(total / 1073741824.0) + "gb" : "?/?gb";
    }
    private static MutableComponent colored(String text, int color) {
        return Component.literal(text).withStyle(style -> style.withColor(color));
    }
    private static MutableComponent white(String text) { return colored(text, 0xFFFFFF); }
    private static int advance(Component line, boolean compact) {
        return compact && line.getString().isEmpty() ? 6 : 12;
    }
    private static Layout layout(int width) {
        var lines = lines();
        if (lines.isEmpty()) return null;
        Minecraft client = Minecraft.getInstance();
        int height = client.getWindow().getGuiScaledHeight();
        boolean effects = !client.player.getActiveEffects().isEmpty();
        if (cachedLayout != null && cachedLayout.lines() == lines && cachedLayout.width() == width
                && cachedLayout.height() == height && cachedLayout.effects() == effects
                && cachedLayout.language() == Language.getInstance()) return cachedLayout;
        if (measuredLanguage != Language.getInstance()) {
            java.util.Arrays.fill(MEASURED, null); measuredLanguage = Language.getInstance();
        }
        boolean compact = lines.size() * 12 > height - 96;
        int end = compact ? lines.size() - 3 : lines.size();
        int widest = 1, bodyHeight = 0, extraWidth = 1;
        for (int i = 0; i < lines.size(); i++) {
            int row = !cachedReduced || i < 10 ? i : i + 8;
            if (MEASURED[row] != ROWS[row]) {
                WIDTHS[row] = client.font.width(ROWS[row]); MEASURED[row] = ROWS[row];
            }
            int lineWidth = WIDTHS[row];
            if (i < end) {
                widest = Math.max(widest, lineWidth);
                bodyHeight += advance(lines.get(i), compact);
            } else extraWidth = Math.max(extraWidth, lineWidth);
        }
        var body = new HudLayout.Rectangle(5, 5, (int)Math.ceil(widest * FONT_SCALE) + 6, bodyHeight + 6);
        int scaledExtra = (int)Math.ceil(extraWidth * FONT_SCALE);
        List<HudLayout.Rectangle> extra = compact ? List.of(new HudLayout.Rectangle(width - scaledExtra - 10,
                (effects ? 60 : 8) - 2, scaledExtra + 4, 40)) : List.of();
        cachedLayout = new Layout(lines, width, height, effects, compact, Language.getInstance(), end, body, extra);
        return cachedLayout;
    }
    public static int counterTop(int normalY) {
        var layout = layout(Minecraft.getInstance().getWindow().getGuiScaledWidth());
        if (layout == null || !layout.compact()) return normalY;
        return Math.max(normalY, (layout.effects() ? 60 : 8) + 42);
    }
    public static List<HudLayout.Rectangle> extraBounds(int width) {
        var layout = layout(width);
        return layout == null ? List.of() : layout.extra();
    }
    public static HudLayout.Rectangle bounds(int width) {
        var layout = layout(width);
        return layout == null ? null : layout.body();
    }
    public static void render(GuiGraphicsExtractor graphics) {
        var layout = layout(graphics.guiWidth());
        if (layout == null) return;
        var lines = layout.lines();
        int end = layout.bodyEnd(), y = 8;
        for (int i = 0; i < end; i++) {
            drawLine(graphics, lines.get(i), 8, y);
            y += advance(lines.get(i), layout.compact());
        }
        if (layout.compact()) {
            var box = layout.extra().getFirst();
            for (int i = end; i < lines.size(); i++) drawLine(graphics, lines.get(i), box.x() + 2, box.y() + 2 + (i - end) * 12);
        }
    }
    private static void drawLine(GuiGraphicsExtractor graphics, Component text, int x, int y) {
        if (text.getString().isEmpty()) return;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(FONT_SCALE, FONT_SCALE);
        graphics.text(Minecraft.getInstance().font, text, 0, 0, 0xFFFFFFFF, true);
        graphics.pose().popMatrix();
    }
}
