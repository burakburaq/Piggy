package dev.piggy.n7q4m2.client.quality.hud;

import dev.piggy.n7q4m2.client.quality.QualityClient;
import dev.piggy.n7q4m2.quality.metrics.FoodPreview;
import dev.piggy.n7q4m2.quality.metrics.HudLayout;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HudFeatures {
    private HudFeatures() {}
    public static void initClient() {
        ClientMetrics.initClient();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { invalidate(); SimpleDebug.invalidate(); FoodHud.invalidate(); });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("piggy", "quality_hud"), HudFeatures::render);
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            if (!QualityClient.config.foodInfo) return;
            var food = stack.get(DataComponents.FOOD);
            if (food == null) return;
            lines.add(Component.translatable("piggy.hud.food_values", food.nutrition(), decimal(food.saturation()))
                    .withStyle(ChatFormatting.GOLD));
            Minecraft client = Minecraft.getInstance();
            float saturation = ClientMetrics.saturation();
            if (client.player != null && saturation >= 0 && stack.get(DataComponents.CONSUMABLE) != null
                    && client.player.canEat(food.canAlwaysEat())) {
                var preview = FoodPreview.afterEating(client.player.getFoodData().getFoodLevel(), saturation,
                        food.nutrition(), food.saturation());
                lines.add(Component.translatable("piggy.hud.hunger",
                        client.player.getFoodData().getFoodLevel() + " → " + preview.hunger()).withStyle(ChatFormatting.GOLD));
                lines.add(Component.translatable("piggy.hud.saturation",
                        decimal(saturation) + " → " + decimal(preview.saturation())).withStyle(ChatFormatting.YELLOW));
                float recovery = FoodHud.recovery(client.player, stack, preview);
                if (recovery > 0) lines.add(Component.translatable("piggy.hud.health_preview", decimal(recovery))
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        });
    }

    private record Counter(ItemStack icon, Identifier texture, String count) {}
    private static final Identifier[] ROCKET_TEXTURES = {
            Identifier.fromNamespaceAndPath("piggy", "textures/gui/rocket_1.png"),
            Identifier.fromNamespaceAndPath("piggy", "textures/gui/rocket_2.png"),
            Identifier.fromNamespaceAndPath("piggy", "textures/gui/rocket_3.png")};
    private static Object cachedPlayer, cachedLevel;
    private static int cachedTick, cachedSlot;
    private static boolean cachedArrows, cachedRockets;
    private static ItemStack cachedWeapon = ItemStack.EMPTY;
    private static List<List<Counter>> cachedGroups = List.of();
    private static final ItemStack[] NO_AMMO = new ItemStack[0];
    private static ItemStack[] cachedAmmo = NO_AMMO;
    private record CounterLayout(List<List<Counter>> groups, int width, int height, int y, int available,
                                 Object language, int[] cells, int[][] textWidths, HudLayout.Frame frame) {}
    private static CounterLayout cachedLayout;
    private static List<HudLayout.Rectangle> occupied = List.of();
    public static List<HudLayout.Rectangle> bounds() { return occupied; }
    private static void invalidate() {
        cachedPlayer = null; cachedLevel = null; cachedWeapon = ItemStack.EMPTY;
        cachedGroups = List.of(); occupied = List.of(); cachedLayout = null;
        cachedAmmo = NO_AMMO;
    }
    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.gui.hud.isHidden() || client.gui.screen() != null) {
            if (cachedPlayer != null) invalidate(); return;
        }
        ItemStack weapon = QualityClient.config.arrowCounter ? heldWeapon(client) : ItemStack.EMPTY;
        boolean arrows = !weapon.isEmpty();
        boolean rockets = QualityClient.config.rocketCounter && client.player.isFallFlying();
        if (!arrows && !rockets) { if (cachedPlayer != null) invalidate(); return; }
        // Inventory packets and local uses are reflected by the next client tick (at most 50 ms at 20 TPS).
        boolean ownerChanged = cachedPlayer != client.player || cachedLevel != client.level;
        boolean modeChanged = cachedArrows != arrows || cachedRockets != rockets;
        boolean weaponChanged = !ItemStack.isSameItemSameComponents(cachedWeapon, weapon);
        boolean slotChanged = cachedSlot != client.player.getInventory().getSelectedSlot();
        if (ownerChanged || modeChanged || weaponChanged || slotChanged || cachedTick != client.player.tickCount) {
            boolean ammoChanged = updateAmmo(client, arrows, rockets);
            cachedTick = client.player.tickCount;
            if (ownerChanged || modeChanged || weaponChanged || slotChanged || ammoChanged) {
                cachedPlayer = client.player; cachedLevel = client.level;
                cachedSlot = client.player.getInventory().getSelectedSlot();
                cachedArrows = arrows; cachedRockets = rockets;
                if (ownerChanged || weaponChanged) cachedWeapon = weapon.copy();
                List<List<Counter>> groups = new ArrayList<>(2);
                if (arrows) {
                    List<Counter> content = new ArrayList<>();
                    for (var group : AmmoGroups.arrows(client.player, weapon))
                        content.add(new Counter(group.representative().copyWithCount(1), null, Integer.toString(group.count())));
                    if (content.isEmpty()) content.add(new Counter(new ItemStack(Items.ARROW), null, "0"));
                    groups.add(List.copyOf(content));
                }
                if (rockets) {
                    List<Counter> content = new ArrayList<>(3);
                    for (var group : AmmoGroups.rockets(client.player)) content.add(new Counter(group.icon(),
                            ROCKET_TEXTURES[group.flight() - 1], Integer.toString(group.count())));
                    groups.add(List.copyOf(content));
                }
                cachedGroups = List.copyOf(groups);
            }
        }
        int counterY = client.player.getActiveEffects().isEmpty() ? 8 : 60;
        renderGroups(graphics, cachedGroups, SimpleDebug.counterTop(counterY));
    }
    private static ItemStack heldWeapon(Minecraft client) {
        ItemStack main = client.player.getMainHandItem();
        if (main.is(Items.BOW) || main.is(Items.CROSSBOW)) return main;
        ItemStack off = client.player.getOffhandItem();
        return off.is(Items.BOW) || off.is(Items.CROSSBOW) ? off : ItemStack.EMPTY;
    }
    private static boolean updateAmmo(Minecraft client, boolean arrows, boolean rockets) {
        var inventory = client.player.getInventory();
        boolean changed = false;
        if (cachedAmmo.length != inventory.getContainerSize()) {
            cachedAmmo = new ItemStack[inventory.getContainerSize()];
            java.util.Arrays.fill(cachedAmmo, ItemStack.EMPTY); changed = true;
        }
        for (int slot = 0; slot < cachedAmmo.length; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!(arrows && stack.is(ItemTags.ARROWS) || rockets && stack.is(Items.FIREWORK_ROCKET))) stack = ItemStack.EMPTY;
            if (!ItemStack.matches(cachedAmmo[slot], stack)) {
                cachedAmmo[slot] = stack.copy(); changed = true;
            }
        }
        return changed;
    }
    private static void renderGroups(GuiGraphicsExtractor graphics, List<List<Counter>> groups, int y) {
        if (groups.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        var debug = SimpleDebug.bounds(graphics.guiWidth());
        int availableWidth = debug == null ? graphics.guiWidth() / 2 - 16
                : Math.max(22, graphics.guiWidth() - debug.x() - debug.width() - 12);
        var layout = cachedLayout;
        if (layout == null || layout.groups() != groups || layout.width() != graphics.guiWidth()
                || layout.height() != graphics.guiHeight() || layout.y() != y || layout.available() != availableWidth
                || layout.language() != Language.getInstance()) {
            int[] counts = new int[groups.size()], cells = new int[groups.size()];
            int[][] textWidths = new int[groups.size()][];
            for (int i = 0; i < groups.size(); i++) {
                counts[i] = groups.get(i).size();
                textWidths[i] = new int[counts[i]]; cells[i] = 22;
                for (int j = 0; j < counts[i]; j++) {
                    int width = client.font.width(groups.get(i).get(j).count());
                    textWidths[i][j] = width; cells[i] = Math.max(cells[i], width + 4);
                }
            }
            var frame = HudLayout.counters(graphics.guiWidth(), graphics.guiHeight(), y, counts, cells, availableWidth);
            layout = new CounterLayout(groups, graphics.guiWidth(), graphics.guiHeight(), y, availableWidth,
                    Language.getInstance(), cells, textWidths, frame);
            cachedLayout = layout;
            int x = graphics.guiWidth() - frame.width() - 8;
            occupied = List.of(new HudLayout.Rectangle(x - 2, y - 2, frame.width() + 4, frame.height() + 4));
        }
        var frame = layout.frame();
        int x = graphics.guiWidth() - frame.width() - 8;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(frame.scale(), frame.scale());
        // Submit all icons together, then all counts in one upper layer. One stratum per icon
        // prevented batching and allocated a separate GUI tree for each ammunition group.
        for (int group = 0; group < groups.size(); group++) {
            var content = groups.get(group);
            var panel = frame.panels().get(group);
            int cellWidth = layout.cells()[group];
            for (int i = 0; i < content.size(); i++) {
                Counter counter = content.get(i);
                int cx = (i % panel.columns()) * cellWidth;
                int cy = panel.y() + (i / panel.columns()) * 20;
                int iconX = cx + (cellWidth - 16) / 2;
                if (counter.texture() == null) graphics.item(counter.icon(), iconX, cy);
                else graphics.blit(RenderPipelines.GUI_TEXTURED, counter.texture(), iconX, cy, 0, 0, 16, 16, 16, 16);
            }
        }
        graphics.nextStratum();
        for (int group = 0; group < groups.size(); group++) {
            var content = groups.get(group);
            var panel = frame.panels().get(group);
            int cellWidth = layout.cells()[group];
            for (int i = 0; i < content.size(); i++) {
                Counter counter = content.get(i);
                int cx = (i % panel.columns()) * cellWidth;
                int cy = panel.y() + (i / panel.columns()) * 20;
                graphics.text(client.font, counter.count(), cx + (cellWidth - layout.textWidths()[group][i]) / 2,
                        cy + 9, 0xFFFFFFFF, true);
            }
        }
        graphics.pose().popMatrix();
    }
    private static String decimal(float value) { return String.format(Locale.ROOT, "%.1f", value); }
}
