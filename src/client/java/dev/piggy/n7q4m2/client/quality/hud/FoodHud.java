package dev.piggy.n7q4m2.client.quality.hud;

import dev.piggy.n7q4m2.client.quality.QualityClient;
import dev.piggy.n7q4m2.quality.metrics.FoodPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import java.util.List;

/** Decorations of vanilla HUD cells, using their actual positions including shake/bounce. */
public final class FoodHud {
    public static final class Cell {
        private int index, x, y;
        public Cell(int index, int x, int y) { set(index, x, y); }
        private void set(int index, int x, int y) { this.index = index; this.x = x; this.y = y; }
        public int index() { return index; }
        public int x() { return x; }
        public int y() { return y; }
    }
    /** Coordinates are submitted immediately; reuse cells without retaining players or worlds. */
    public static final class Cells extends java.util.AbstractList<Cell> {
        private Cell[] cells = new Cell[10];
        private int size;
        public Cell append(int index, int x, int y) {
            if (size == cells.length) cells = java.util.Arrays.copyOf(cells, cells.length * 2);
            Cell cell = cells[size];
            if (cell == null) cells[size] = cell = new Cell(index, x, y);
            else cell.set(index, x, y);
            size++; return cell;
        }
        @Override public Cell get(int index) { return cells[java.util.Objects.checkIndex(index, size)]; }
        @Override public int size() { return size; }
        @Override public void clear() { size = 0; }
    }
    private static final Identifier HUNGER_GAIN = mask("hunger_gain"), SATURATION_OUTLINE = mask("saturation_outline"), HEALTH_GAIN = mask("health_gain");
    private record RecoveryKey(Player player, Object consumable, FoodPreview preview, float health, float maxHealth,
                               float exhaustion, boolean natural, boolean peaceful) {}
    private static RecoveryKey recoveryKey;
    private static float cachedRecovery;
    private static Player predictionPlayer;
    private static Object predictionFood, predictionConsumable;
    private static int predictionHunger;
    private static float predictionSaturation;
    private static boolean predictionCanEat;
    private static FoodPreview cachedPrediction;
    private static int pulseTick = Integer.MIN_VALUE, pulseAlpha;
    public static void invalidate() {
        recoveryKey = null; predictionPlayer = null; predictionFood = null; predictionConsumable = null; cachedPrediction = null;
    }
    private FoodHud() {}
    private static Identifier mask(String name) { return Identifier.fromNamespaceAndPath("piggy", "textures/gui/" + name + ".png"); }
    /** Crop texture coordinates instead of changing the GPU scissor for every HUD cell. */
    private static void slice(GuiGraphicsExtractor graphics, Identifier texture, Cell cell, int left, int right, int color) {
        if (right <= left) return;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, cell.x() + left, cell.y(), left, 0, right - left, 9, 9, 9, color);
    }
    public static boolean enabled(Player player) {
        Minecraft mc = Minecraft.getInstance();
        return QualityClient.config.foodInfo && mc.player == player && mc.gui.screen() == null && !mc.gui.hud.isHidden()
                && !player.isSpectator() && !player.hasInfiniteMaterials();
    }
    public static ItemStack heldFood(Player player) {
        var main = player.getMainHandItem();
        return main.get(DataComponents.FOOD) != null ? main : player.getOffhandItem();
    }
    public static FoodPreview prediction(Player player, ItemStack stack) {
        var food = stack.get(DataComponents.FOOD);
        var consumable = stack.get(DataComponents.CONSUMABLE);
        float saturation = ClientMetrics.saturation();
        int hunger = player.getFoodData().getFoodLevel();
        boolean canEat = food != null && consumable != null && saturation >= 0 && player.canEat(food.canAlwaysEat());
        if (predictionPlayer == player && predictionFood == food && predictionConsumable == consumable
                && predictionHunger == hunger && predictionSaturation == saturation && predictionCanEat == canEat) return cachedPrediction;
        predictionPlayer = player; predictionFood = food; predictionConsumable = consumable;
        predictionHunger = hunger; predictionSaturation = saturation; predictionCanEat = canEat;
        cachedPrediction = canEat ? FoodPreview.afterEating(hunger, saturation, food.nutrition(), food.saturation()) : null;
        return cachedPrediction;
    }
    public static float recovery(Player player, ItemStack stack, FoodPreview preview) {
        float health = player.getHealth(), maxHealth = player.getMaxHealth();
        float exhaustion = ClientMetrics.exhaustion();
        boolean natural = ClientMetrics.naturalRegeneration(), peaceful = player.level().getDifficulty() == Difficulty.PEACEFUL;
        var consumable = stack.get(DataComponents.CONSUMABLE);
        var key = recoveryKey;
        if (key != null && key.player() == player && java.util.Objects.equals(key.consumable(), consumable)
                && key.preview().equals(preview) && key.health() == health && key.maxHealth() == maxHealth
                && key.exhaustion() == exhaustion && key.natural() == natural && key.peaceful() == peaceful) return cachedRecovery;
        float missing = Math.max(0, maxHealth - health);
        float effects = 0;
        if (consumable != null) for (var effect : consumable.onConsumeEffects()) {
            // Random effects are not promised by the HUD.
            if (!(effect instanceof ApplyStatusEffectsConsumeEffect applied) || applied.probability() < 1) continue;
            for (var instance : applied.effects()) {
                if (instance.is(MobEffects.REGENERATION)) {
                    int interval = Math.max(1, 50 >> Math.min(10, instance.getAmplifier()));
                    effects += instance.getDuration() / (float)interval;
                } else if (instance.is(MobEffects.INSTANT_HEALTH)) effects += 4 << Math.min(10, instance.getAmplifier());
            }
        }
        float regenerated = preview.recovery(exhaustion, Math.max(0, missing - effects), natural, peaceful);
        recoveryKey = new RecoveryKey(player, consumable, preview, health, maxHealth, exhaustion, natural, peaceful);
        cachedRecovery = Math.min(missing, effects + regenerated);
        return cachedRecovery;
    }
    private static int pulse(Player player, int rgb) {
        if (pulseTick != player.tickCount) {
            pulseTick = player.tickCount;
            pulseAlpha = 110 + (int)(55 * (1 + Math.sin(pulseTick * .2)));
        }
        return pulseAlpha << 24 | rgb;
    }
    /** Draw underneath vanilla's actual fill, so odd hunger never recolors the existing half. */
    public static void renderHungerCell(GuiGraphicsExtractor graphics, Player player, Cell cell) {
        if (!enabled(player)) return;
        var preview = prediction(player, heldFood(player));
        if (preview == null || preview.hunger() <= player.getFoodData().getFoodLevel()) return;
        int filled = preview.hunger() - cell.index() * 2;
        if (filled <= 0) return;
        slice(graphics, HUNGER_GAIN, cell, filled == 1 ? 4 : 0, 9, pulse(player, 0x8FFFA4));
    }
    public static void renderFood(GuiGraphicsExtractor graphics, Player player, List<Cell> cells) {
        if (!enabled(player)) return;
        float saturation = ClientMetrics.saturation();
        if (saturation < 0) return;
        var preview = prediction(player, heldFood(player));
        Identifier outline = SATURATION_OUTLINE;
        int futureColor = pulse(player, 0xFFE99A);
        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            int current = FoodPreview.filledPixels(saturation, cell.index());
            int future = FoodPreview.filledPixels(preview == null ? saturation : preview.saturation(), cell.index());
            if (future > current) {
                slice(graphics, outline, cell, 9 - future, 9 - current, futureColor);
            }
            if (current > 0) {
                slice(graphics, outline, cell, 9 - current, 9, 0xFFFFC649);
            }
        }
    }
    public static void renderHearts(GuiGraphicsExtractor graphics, Player player, List<Cell> cells) {
        if (!enabled(player)) return;
        ItemStack food = heldFood(player);
        var preview = prediction(player, food);
        if (preview == null) return;
        float current = player.getHealth(), after = current + recovery(player, food, preview);
        if (after <= current) return;
        Identifier full = HEALTH_GAIN;
        int color = pulse(player, 0xFFA8CF);
        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            float before = Math.clamp((current - cell.index() * 2) / 2f, 0, 1);
            float filled = Math.clamp((after - cell.index() * 2) / 2f, 0, 1);
            if (filled <= before || cell.index() * 2 >= player.getMaxHealth()) continue;
            slice(graphics, full, cell, Math.round(before * 9), Math.round(filled * 9), color);
        }
    }
}
