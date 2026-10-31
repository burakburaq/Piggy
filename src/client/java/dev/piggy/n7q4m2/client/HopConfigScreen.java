package dev.piggy.n7q4m2.client;

import dev.piggy.n7q4m2.config.ServerPolicy;
import dev.piggy.n7q4m2.client.quality.movement.FreeCamera;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.math.BigDecimal;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

public final class HopConfigScreen extends Screen {
    private final Screen parent;
    private ClientConfig editing = PiggyClient.config.copy();
    public enum Page { BUNNY, SPEED_HUD, FREE_CAMERA }
    private final Page page;
    private boolean editingFreeCamera;
    private final Set<String> invalidFields = new HashSet<>();
    private Button saveButton;
    public HopConfigScreen(Screen parent) { this(parent, Page.BUNNY); }
    public HopConfigScreen(Screen parent, Page page) {
        super(Component.translatable(page == Page.BUNNY ? "piggy.menu.bunny" : page == Page.SPEED_HUD ? "piggy.option.show_hud" : "piggy.menu.free_camera"));
        this.parent = parent; this.page = page; this.editingFreeCamera = FreeCamera.active();
    }

    @Override protected void init() {
        invalidFields.clear();
        saveButton = null;
        int columnWidth = Math.min(260, width - 24), x = (width - columnWidth) / 2;
        int y = Math.max(48, (height - 228) / 2);
        int step = Math.max(20, Math.min(23, (height - 99) / 7));
        if (page == Page.BUNNY) {
            toggle("enabled", x, y, columnWidth, () -> editing.bhopEnabled, v -> editing.bhopEnabled = v);
            toggle("autojump", x, y + step, columnWidth, () -> editing.autoJump, v -> editing.autoJump = v);
            ServerPolicy p = PiggyClient.policy;
            slider("air_accel", x, y + step * 2, columnWidth, 1, PiggyClient.supported ? p.maxAirAcceleration : 40, editing.airAcceleration, v -> editing.airAcceleration = v);
            slider("air_friction", x, y + step * 3, columnWidth, PiggyClient.supported ? p.minAirFriction : .1, PiggyClient.supported ? p.maxAirFriction : 20, editing.airFriction, v -> editing.airFriction = v);
            slider("speed_cap", x, y + step * 4, columnWidth, PiggyClient.supported ? p.minSpeedCapBps : 5, PiggyClient.supported ? p.maxSpeedCapBps : 200, editing.maxSpeedBps, v -> editing.maxSpeedBps = v);
        } else if (page == Page.SPEED_HUD) {
            toggle("show_hud", x, y, columnWidth, () -> editing.showHud, v -> editing.showHud = v);
            slider("hud_x", x, y + 23, columnWidth, 0, 100, editing.hudX * 100, v -> editing.hudX = v / 100);
            slider("hud_y", x, y + 46, columnWidth, 0, 100, editing.hudY * 100, v -> editing.hudY = v / 100);
        } else {
            toggle("free_camera", x, y, columnWidth, () -> editingFreeCamera, v -> editingFreeCamera = v);
            slider("free_camera_speed", x, y + step, columnWidth, 1, 100, editing.freeCameraSpeedBps, v -> editing.freeCameraSpeedBps = v);
        }
        int footer = Math.min(height - 28, y + 182);
        addRenderableWidget(Button.builder(Component.translatable("piggy.reset"), b -> {
            ClientConfig defaults = new ClientConfig();
            if (page == Page.BUNNY) {
                editing.bhopEnabled = defaults.bhopEnabled; editing.autoJump = defaults.autoJump;
                editing.airAcceleration = defaults.airAcceleration; editing.airFriction = defaults.airFriction; editing.maxSpeedBps = defaults.maxSpeedBps;
            } else if (page == Page.SPEED_HUD) {
                editing.showHud = defaults.showHud; editing.hudX = defaults.hudX; editing.hudY = defaults.hudY;
            } else { editingFreeCamera = false; editing.freeCameraSpeedBps = defaults.freeCameraSpeedBps; }
            rebuildWidgets();
        }).bounds(x, footer - 23, columnWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(x, footer, columnWidth / 2 - 2, 20).build());
        saveButton = addRenderableWidget(Button.builder(Component.translatable("piggy.save"), b -> {
            PiggyClient.save(editing);
            if (page == Page.FREE_CAMERA) FreeCamera.setEnabled(editingFreeCamera);
            onClose();
        }).bounds(x + columnWidth / 2 + 2, footer, columnWidth / 2 - 2, 20).build());
    }

    private void toggle(String key, int x, int y, int w, BooleanSupplier get, Consumer<Boolean> set) {
        Button button = Button.builder(toggleLabel(key, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean()); b.setMessage(toggleLabel(key, get.getAsBoolean()));
        }).bounds(x, y, w, 20).build();
        button.setTooltip(Tooltip.create(Component.translatable("piggy.option." + key + ".tip")));
        addRenderableWidget(button);
    }
    private static Component toggleLabel(String key, boolean value) {
        return Component.translatable("piggy.option." + key).append(": ").append(Component.translatable(value ? "options.on" : "options.off"));
    }
    private void slider(String key, int x, int y, int w, double min, double max, double initial, Consumer<Double> setter) {
        double clamped = Math.max(min, Math.min(max, initial));
        setter.accept(clamped);
        int fieldWidth = 56;
        var field = new EditBox(font, x, y, fieldWidth, 20, Component.translatable("piggy.option." + key));
        field.setMaxLength(18);
        field.setValue(formatNumber(clamped));
        field.setTooltip(Tooltip.create(Component.translatable("piggy.number.tip", formatNumber(min), formatNumber(max))));
        var slider = new AbstractSliderButton(x + fieldWidth + 4, y, w - fieldWidth - 4, 20, Component.empty(), max == min ? 0 : (clamped - min) / (max - min)) {
            { updateMessage(); }
            @Override protected void updateMessage() {
                setMessage(Component.translatable("piggy.option." + key));
            }
            @Override protected void applyValue() {
                double numeric = Math.round((min + value * (max - min)) * 1000) / 1000.0;
                numeric = Math.max(min, Math.min(max, numeric));
                setter.accept(numeric);
                field.setValue(formatNumber(numeric));
            }
            public void setNumericValue(double numeric) {
                value = max == min ? 0 : (numeric - min) / (max - min);
                updateMessage();
            }
        };
        field.setResponder(text -> {
            boolean valid = false;
            try {
                double numeric = Double.parseDouble(text.trim().replace(',', '.'));
                if (Double.isFinite(numeric) && numeric >= min && numeric <= max) {
                    setter.accept(numeric); slider.setNumericValue(numeric); valid = true;
                }
            } catch (NumberFormatException ignored) {}
            if (valid) invalidFields.remove(key); else invalidFields.add(key);
            field.setTextColor(valid ? 0xFFFFFFFF : 0xFFFF7777);
            if (saveButton != null) saveButton.active = invalidFields.isEmpty();
        });
        slider.active = max > min;
        slider.setTooltip(Tooltip.create(Component.translatable("piggy.option." + key + ".tip")
                .append("\n" + String.format(Locale.ROOT, "%.1f – %.1f", min, max))));
        addRenderableWidget(slider);
        addRenderableWidget(field);
    }

    private static String formatNumber(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 3, 0xFFFFFFFF);
        String status = minecraft.player == null ? "piggy.status.offline"
                : !PiggyClient.supported ? "piggy.status.client_only"
                : PiggyClient.policy.enabled ? "piggy.status.server" : "piggy.status.unsupported";
        if (page == Page.BUNNY) graphics.centeredText(font, Component.translatable(status), width / 2, 15, 0xFFBBBBBB);
        if (page == Page.SPEED_HUD && editing.showHud) SpeedHud.draw(graphics, editing, 12.3, true);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
