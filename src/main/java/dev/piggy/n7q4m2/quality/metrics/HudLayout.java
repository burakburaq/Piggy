package dev.piggy.n7q4m2.quality.metrics;

import java.util.ArrayList;
import java.util.List;

/** Shared, testable bounds for all Piggy HUD panels. */
public final class HudLayout {
    private HudLayout() {}
    public record Panel(int columns, int width, int height, int y) {}
    public record Frame(float scale, int width, int height, List<Panel> panels) {}
    public record Rectangle(int x, int y, int width, int height) {
        public boolean overlaps(Rectangle b) {
            return x < b.x + b.width && x + width > b.x && y < b.y + b.height && y + height > b.y;
        }
        public boolean inside(int w, int h) { return x >= 1 && y >= 1 && x + width < w && y + height < h; }
    }
    public static Frame counters(int screenWidth, int screenHeight, int y, int[] counts, int[] cells) {
        return counters(screenWidth, screenHeight, y, counts, cells, Math.max(44, screenWidth / 2 - 16));
    }
    public static Frame counters(int screenWidth, int screenHeight, int y, int[] counts, int[] cells, int availableWidth) {
        int availableHeight = Math.max(20, screenHeight - 96 - y);
        Frame last = null;
        for (int step = 20; step >= 4; step--) {
            float scale = step / 20f;
            int columnsWidth = (int) (availableWidth / scale);
            var panels = new ArrayList<Panel>();
            int bottom = 0, widest = 0;
            for (int i = 0; i < counts.length; i++) {
                int columns = Math.min(counts[i], Math.max(1, columnsWidth / cells[i]));
                int width = columns * cells[i];
                int height = ((counts[i] + columns - 1) / columns) * 20;
                panels.add(new Panel(columns, width, height, bottom));
                bottom += height + 6;
                widest = Math.max(widest, width);
            }
            int height = Math.max(0, bottom - 6);
            last = new Frame(scale, (int)Math.ceil(widest * scale), (int)Math.ceil(height * scale), List.copyOf(panels));
            if (last.width <= availableWidth && last.height <= availableHeight) return last;
        }
        return last;
    }
    public static Rectangle avoid(Rectangle preferred, List<Rectangle> obstacles, int w, int h) {
        if (preferred.inside(w, h) && clear(preferred, obstacles)) return preferred;
        var xs = new ArrayList<Integer>();
        var ys = new ArrayList<Integer>();
        xs.add(preferred.x); xs.add(4); xs.add(w - preferred.width - 4);
        ys.add(preferred.y); ys.add(4); ys.add(h - preferred.height - 4);
        for (var obstacle : obstacles) {
            xs.add(obstacle.x - preferred.width - 3); xs.add(obstacle.x + obstacle.width + 3);
            ys.add(obstacle.y - preferred.height - 3); ys.add(obstacle.y + obstacle.height + 3);
        }
        Rectangle best = preferred;
        long bestDistance = Long.MAX_VALUE;
        for (int x : xs) for (int y : ys) {
            var candidate = new Rectangle(x, y, preferred.width, preferred.height);
            if (!candidate.inside(w, h) || !clear(candidate, obstacles)) continue;
            long distance = (long)(x - preferred.x) * (x - preferred.x) + (long)(y - preferred.y) * (y - preferred.y);
            if (distance < bestDistance) { best = candidate; bestDistance = distance; }
        }
        return best;
    }
    private static boolean clear(Rectangle candidate, List<Rectangle> obstacles) {
        for (var obstacle : obstacles) if (candidate.overlaps(obstacle)) return false;
        return true;
    }
}
