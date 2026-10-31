package dev.piggy.n7q4m2.hud;

import dev.piggy.n7q4m2.quality.metrics.FoodPreview;
import dev.piggy.n7q4m2.quality.metrics.OrderedGroups;
import dev.piggy.n7q4m2.quality.metrics.TickWindow;
import dev.piggy.n7q4m2.quality.metrics.HudLayout;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HudLogicTest {
    @Test void saturationStartsAtRightEdgeAndFillsExistingCellBeforeNext() {
        assertEquals(new FoodPreview.Slice(4, 9), FoodPreview.rightSlice(0, 1, 0));
        assertEquals(new FoodPreview.Slice(7, 9), FoodPreview.rightSlice(0, .5f, 0));
        assertEquals(new FoodPreview.Slice(4, 7), FoodPreview.rightSlice(.5f, 1, 0));
        assertEquals(new FoodPreview.Slice(0, 4), FoodPreview.rightSlice(1, 2, 0));
        assertEquals(new FoodPreview.Slice(9, 9), FoodPreview.rightSlice(0, 2, 1));
        assertEquals(new FoodPreview.Slice(7, 9), FoodPreview.rightSlice(0, 2.5f, 1));
        assertEquals(new FoodPreview.Slice(0, 7), FoodPreview.rightSlice(2.5f, 4.9f, 1));
        assertEquals(new FoodPreview.Slice(5, 9), FoodPreview.rightSlice(2.5f, 4.9f, 2));
    }
    @Test void allOrderedArrowGroupsAndRocketsFitMinimumScreenWithEffects() {
        for (int w : List.of(320, 427, 854)) for (int y : List.of(8, 60)) {
            var frame = HudLayout.counters(w, 240, y, new int[]{36, 3}, new int[]{22, 28});
            assertTrue(frame.width() <= w / 2 - 16);
            assertTrue(frame.height() <= 240 - 96 - y);
            assertTrue(frame.scale() >= .5);
            assertEquals(2, frame.panels().size());
        }
    }
    @Test void speedHudMovesOutOfOtherPanelsWithoutLeavingScreen() {
        var preferred = new HudLayout.Rectangle(5, 5, 48, 15);
        var occupied = List.of(new HudLayout.Rectangle(5, 5, 145, 132),
                new HudLayout.Rectangle(170, 5, 145, 134), new HudLayout.Rectangle(5, 121, 146, 45));
        var actual = HudLayout.avoid(preferred, occupied, 320, 240);
        assertTrue(actual.inside(320, 240));
        assertTrue(occupied.stream().noneMatch(actual::overlaps));
    }
    private record Ammo(String componentKey, int count) {}
    @Test void orderedAmmunitionKeepsSeparatedIdenticalGroups() {
        var groups = OrderedGroups.group(List.of(new Ammo("normal", 64), new Ammo("normal", 12),
                new Ammo("poison", 8), new Ammo("normal", 3)),
                (a, b) -> a.componentKey().equals(b.componentKey()), Ammo::count);
        assertEquals(List.of(76, 8, 3), groups.stream().map(OrderedGroups.Group::count).toList());
    }
    @Test void distinctPotionComponentsDoNotMerge() {
        var groups = OrderedGroups.group(List.of(new Ammo("poison:short", 4), new Ammo("poison:long", 5)),
                (a, b) -> a.componentKey().equals(b.componentKey()), Ammo::count);
        assertEquals(2, groups.size());
    }
    @Test void foodPreviewCapsSaturationAtResultingHunger() {
        assertEquals(new FoodPreview(20, 20), FoodPreview.afterEating(18, 19, 6, 14));
        assertEquals(new FoodPreview(8, 8), FoodPreview.afterEating(4, 3, 4, 10));
    }
    @Test void recoveryHonorsRegenerationThresholdAndExhaustion() {
        assertEquals(0, new FoodPreview(17, 10).recovery(0, 10, true, false));
        assertEquals(0, new FoodPreview(20, 20).recovery(0, 10, false, false));
        assertEquals(1, new FoodPreview(18, 0).recovery(0, 10, true, false));
        assertEquals(0, new FoodPreview(18, 0).recovery(4.1f, 10, true, false));
        assertEquals(3, new FoodPreview(20, 0).recovery(0, 10, true, false));
        assertEquals(1.25f, new FoodPreview(20, 20).recovery(0, 1.25f, true, false));
        assertEquals(10, new FoodPreview(18, 0).recovery(0, 10, true, true));
    }
    @Test void realTickRateIncludesTimeWaitingBetweenTicks() {
        TickWindow window = new TickWindow();
        assertEquals(-1, window.tps(20));
        for (int tick = 0; tick <= 100; tick++) window.sample(tick * 100_000_000L);
        assertEquals(10, window.tps(20), 0.01);
        // After slow samples age out, the rolling window recovers.
        for (int tick = 1; tick <= 101; tick++) window.sample(10_000_000_000L + tick * 50_000_000L);
        assertEquals(20, window.tps(20), 0.01);
    }
    @Test void suspendAndResetDoNotExposeStaleOrInfiniteTps() {
        TickWindow window = new TickWindow();
        window.sample(1); window.sample(50_000_001);
        assertEquals(20, window.tps(20));
        window.sample(20_000_000_000L);
        assertEquals(-1, window.tps(20));
        window.sample(20_050_000_000L);
        assertEquals(20, window.tps(20));
        assertEquals(-1, window.tps(Float.NaN));
        window.reset();
        assertEquals(-1, window.tps(20));
    }
}
