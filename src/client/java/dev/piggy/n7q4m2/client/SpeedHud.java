package dev.piggy.n7q4m2.client;

import dev.piggy.n7q4m2.movement.HopAccess;
import dev.piggy.n7q4m2.quality.metrics.HudLayout;
import dev.piggy.n7q4m2.quality.metrics.HudNumbers;
import dev.piggy.n7q4m2.client.quality.hud.HudFeatures;
import dev.piggy.n7q4m2.client.quality.hud.SimpleDebug;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.locale.Language;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class SpeedHud {
    private static long labelSpeed = Long.MIN_VALUE;
    private static String speedLabel;
    private static Object labelLanguage;
    private static int labelWidth;
    private static boolean hasPlacement;
    private static int lastWidth, lastHeight, lastX, lastY, lastTextWidth, lastEffects, placedX, placedY;
    private static List<HudLayout.Rectangle> lastAmmo = List.of(), lastExtra = List.of();
    private static HudLayout.Rectangle lastDebug;
    private SpeedHud() {}
    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.screen() != null || client.gui.hud.isHidden() || !PiggyClient.config.showHud) return;
        double speed = client.player.getDeltaMovement().horizontalDistance() * 20;
        boolean active = ((HopAccess) client.player).piggy$state().active();
        if (active && client.player.horizontalCollision)
            speed = Math.max(speed, ((HopAccess) client.player).piggy$state().retainedSpeed() * 20);
        if (speed <= 3.5) return;
        draw(graphics, PiggyClient.config, speed, active);
    }
    public static void draw(GuiGraphicsExtractor graphics, ClientConfig config, double speed, boolean active) {
        Minecraft client = Minecraft.getInstance();
        long roundedSpeed = Math.round(speed * 10);
        boolean labelChanged = roundedSpeed != labelSpeed;
        if (labelChanged) {
            labelSpeed = roundedSpeed;
            speedLabel = HudNumbers.oneDecimal(speed) + " b/s";
        }
        String label = speedLabel;
        if (labelChanged || labelLanguage != Language.getInstance()) {
            labelWidth = client.font.width(label); labelLanguage = Language.getInstance();
        }
        int textWidth = labelWidth;
        int x = 4 + (int) ((graphics.guiWidth() - textWidth - 8) * config.hudX);
        int y = 4 + (int) ((graphics.guiHeight() - 17) * config.hudY);
        if (client.gui.screen() == null) {
            var ammo = HudFeatures.bounds();
            var debug = SimpleDebug.bounds(graphics.guiWidth());
            var extra = SimpleDebug.extraBounds(graphics.guiWidth());
            int effects = client.player == null ? 0 : client.player.getActiveEffects().size();
            if (!hasPlacement || lastWidth != graphics.guiWidth() || lastHeight != graphics.guiHeight()
                    || lastX != x || lastY != y || lastTextWidth != textWidth || lastEffects != effects
                    || !Objects.equals(lastDebug, debug) || !lastAmmo.equals(ammo) || !lastExtra.equals(extra)) {
                var obstacles = new ArrayList<>(ammo);
                if (effects > 0) {
                    int width = Math.min(graphics.guiWidth() / 2, effects * 25);
                    obstacles.add(new HudLayout.Rectangle(graphics.guiWidth() - width - 2, 0, width + 2, 52));
                }
                if (debug != null) obstacles.add(debug);
                obstacles.addAll(extra);
                var placement = HudLayout.avoid(new HudLayout.Rectangle(x - 3, y - 3, textWidth + 6, 15),
                        obstacles, graphics.guiWidth(), graphics.guiHeight());
                placedX = placement.x() + 3; placedY = placement.y() + 3;
                lastWidth = graphics.guiWidth(); lastHeight = graphics.guiHeight();
                lastX = x; lastY = y; lastTextWidth = textWidth; lastEffects = effects;
                lastDebug = debug; lastAmmo = ammo; lastExtra = extra; hasPlacement = true;
            }
            x = placedX; y = placedY;
        }
        graphics.text(client.font, label, x, y, active ? 0xFF75ED9C : 0xFFFFFFFF, true);
    }
}
