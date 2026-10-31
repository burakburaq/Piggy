package dev.piggy.n7q4m2.client;

import dev.piggy.n7q4m2.client.quality.QualityConfigScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Entry point shared by Mod Menu and the O shortcut. */
public final class PiggyConfigScreen extends Screen {
    private final Screen parent;
    public PiggyConfigScreen(Screen parent) { super(Component.literal("Piggy")); this.parent = parent; }
    @Override protected void init() {
        int w = Math.min(340, width - 24), cell = (w - 8) / 2, x = (width - w) / 2;
        int y = Math.max(40, (height - 110) / 2);
        tile("bunny", x, y, cell, () -> new HopConfigScreen(this));
        tile("anti_afk", x + cell + 8, y, cell, () -> new QualityConfigScreen(this, QualityConfigScreen.Section.ANTI_AFK));
        tile("interactive", x, y + 28, cell, () -> new QualityConfigScreen(this, QualityConfigScreen.Section.INTERACTIVE));
        tile("interface", x + cell + 8, y + 28, cell, () -> new QualityConfigScreen(this, QualityConfigScreen.Section.INTERFACE));
        tile("free_camera", x, y + 56, w, () -> new HopConfigScreen(this, HopConfigScreen.Page.FREE_CAMERA));
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x, Math.min(height - 28, y + 90), w, 20).build());
    }
    private void tile(String key, int x, int y, int w, java.util.function.Supplier<Screen> screen) {
        var button = Button.builder(Component.translatable("piggy.menu." + key), b -> minecraft.gui.setScreen(screen.get())).bounds(x, y, w, 20).build();
        button.setTooltip(Tooltip.create(Component.translatable("piggy.menu." + key + ".tip")));
        addRenderableWidget(button);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, Math.max(14, Math.max(40, (height - 110) / 2) - 24), 0xFFFFFFFF);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
