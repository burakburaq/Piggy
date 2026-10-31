package dev.piggy.n7q4m2.client.quality;

import dev.piggy.n7q4m2.client.HopConfigScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class QualityConfigScreen extends Screen {
    public enum Section { INTERFACE, INTERACTIVE, ANTI_AFK }
    private final Screen parent;
    private final Section section;
    private QualityConfig editing = QualityClient.config.copy();
    private boolean antiAfkEdited;
    public QualityConfigScreen(Screen parent) { this(parent, Section.INTERFACE); }
    public QualityConfigScreen(Screen parent, Section section) {
        super(Component.translatable(section == Section.INTERFACE ? "piggy.menu.interface" : section == Section.INTERACTIVE ? "piggy.menu.interactive" : "piggy.menu.anti_afk"));
        this.parent = parent; this.section = section;
    }
    @Override protected void init() {
        int w = Math.min(300, width - 24), x = (width - w) / 2;
        int y = Math.max(32, (height - 218) / 2);
        if (section == Section.INTERFACE) {
            addRenderableWidget(Button.builder(Component.translatable("piggy.option.show_hud"), b ->
                    minecraft.gui.setScreen(new HopConfigScreen(this, HopConfigScreen.Page.SPEED_HUD))).bounds(x, y, w, 20).build());
            toggle("simple_debug", x, y + 22, w, () -> editing.simpleDebug, v -> editing.simpleDebug = v);
            toggle("shulker_preview", x, y + 44, w, () -> editing.shulkerPreview, v -> editing.shulkerPreview = v);
            toggle("food_info", x, y + 66, w, () -> editing.foodInfo, v -> editing.foodInfo = v);
            toggle("arrow_counter", x, y + 88, w, () -> editing.arrowCounter, v -> editing.arrowCounter = v);
            toggle("rocket_counter", x, y + 110, w, () -> editing.rocketCounter, v -> editing.rocketCounter = v);
            toggle("see_through_players", x, y + 132, w, () -> editing.seeThroughPlayers, v -> editing.seeThroughPlayers = v);
        } else if (section == Section.INTERACTIVE) {
            toggle("auto_restock", x, y, w, () -> editing.autoRestock, v -> editing.autoRestock = v);
            toggle("placement_assist", x, y + 23, w, () -> editing.placementAssist, v -> editing.placementAssist = v);
            toggle("paired_doors", x, y + 46, w, () -> editing.pairedDoors, v -> editing.pairedDoors = v);
            toggle("bucket_cycle", x, y + 69, w, () -> editing.bucketCycle, v -> editing.bucketCycle = v);
        } else {
            toggle("anti_afk", x, y, w, () -> editing.antiAfk, v -> { editing.antiAfk = v; antiAfkEdited = true; });
            toggle("anti_afk_jump", x, y + 23, w, () -> editing.antiAfkJump, v -> editing.antiAfkJump = v);
            toggle("anti_afk_crouch", x, y + 46, w, () -> editing.antiAfkCrouch, v -> editing.antiAfkCrouch = v);
        }
        int footer = height - 28;
        addRenderableWidget(Button.builder(Component.translatable("piggy.reset"), b -> {
            QualityConfig d = new QualityConfig();
            if (section == Section.INTERFACE) {
                editing.seeThroughPlayers = d.seeThroughPlayers; editing.simpleDebug = d.simpleDebug; editing.shulkerPreview = d.shulkerPreview; editing.foodInfo = d.foodInfo;
                editing.arrowCounter = d.arrowCounter; editing.rocketCounter = d.rocketCounter;
            } else if (section == Section.INTERACTIVE) {
                editing.bucketCycle = d.bucketCycle; editing.autoRestock = d.autoRestock; editing.placementAssist = d.placementAssist; editing.pairedDoors = d.pairedDoors;
            } else { editing.antiAfk = false; antiAfkEdited = true; editing.antiAfkJump = true; editing.antiAfkCrouch = true; }
            rebuildWidgets();
        }).bounds(x, footer - 23, w, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(x, footer, w / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("piggy.save"), b -> {
            QualityConfig current = QualityClient.config.copy();
            if (section == Section.INTERFACE) {
                current.seeThroughPlayers = editing.seeThroughPlayers; current.simpleDebug = editing.simpleDebug; current.shulkerPreview = editing.shulkerPreview; current.foodInfo = editing.foodInfo;
                current.arrowCounter = editing.arrowCounter; current.rocketCounter = editing.rocketCounter;
            } else if (section == Section.INTERACTIVE) {
                current.bucketCycle = editing.bucketCycle; current.autoRestock = editing.autoRestock; current.placementAssist = editing.placementAssist; current.pairedDoors = editing.pairedDoors;
            } else {
                if (antiAfkEdited) current.antiAfk = editing.antiAfk;
                current.antiAfkJump = editing.antiAfkJump; current.antiAfkCrouch = editing.antiAfkCrouch;
            }
            QualityClient.save(current); onClose();
        }).bounds(x + w / 2 + 2, footer, w / 2 - 2, 20).build());
    }
    @Override public void tick() {
        super.tick();
        if (section == Section.ANTI_AFK && !antiAfkEdited && editing.antiAfk != QualityClient.config.antiAfk) {
            editing.antiAfk = QualityClient.config.antiAfk; rebuildWidgets();
        }
    }
    private void toggle(String key, int x, int y, int w, BooleanSupplier get, Consumer<Boolean> set) {
        Button button = Button.builder(Component.translatable("piggy.quality." + key).append(": ")
                .append(Component.translatable(get.getAsBoolean() ? "options.on" : "options.off")), b -> {
            set.accept(!get.getAsBoolean()); b.setMessage(Component.translatable("piggy.quality." + key).append(": ")
                    .append(Component.translatable(get.getAsBoolean() ? "options.on" : "options.off")));
        }).bounds(x, y, w, 20).build();
        button.setTooltip(Tooltip.create(Component.translatable("piggy.quality." + key + ".tip")));
        addRenderableWidget(button);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
