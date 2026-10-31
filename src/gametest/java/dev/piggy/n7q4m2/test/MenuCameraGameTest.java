package dev.piggy.n7q4m2.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.piggy.n7q4m2.client.*;
import dev.piggy.n7q4m2.client.quality.*;
import dev.piggy.n7q4m2.client.quality.movement.FreeCamera;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Focused visual/navigation and detached-camera checks, without replaying unrelated feature scenarios. */
public final class MenuCameraGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void layout(Screen screen) {
        var widgets = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
        for (int i = 0; i < widgets.size(); i++) {
            var a = widgets.get(i);
            check(a.getX() >= 0 && a.getY() >= 0 && a.getRight() <= screen.width && a.getBottom() <= screen.height, "Widget outside screen: " + a.getMessage());
            for (int j = i + 1; j < widgets.size(); j++) {
                var b = widgets.get(j);
                check(!(a.getX() < b.getRight() && a.getRight() > b.getX() && a.getY() < b.getBottom() && a.getBottom() > b.getY()), "Widgets overlap");
            }
        }
    }
    private static void toggle(ClientGameTestContext context, String key) {
        context.runOnClient(c -> {
            String prefix = Component.translatable(key).getString() + ": ";
            c.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(b -> b.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow().onPress(null);
        });
    }
    private static void shot(ClientGameTestContext context, String name) {
        context.runOnClient(c -> layout(c.gui.screen())); context.waitTicks(2); context.takeScreenshot(name);
    }
    @Override public void runTest(ClientGameTestContext context) {
        var movement = PiggyClient.config.copy(); var quality = QualityClient.config.copy();
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode survival @a");
            context.waitFor(c -> c.player != null && c.gui.screen() == null, 200);
            context.getInput().resizeWindow(640, 480);
            AtomicReference<CompletableFuture<Void>> reload = new AtomicReference<>();
            context.runOnClient(c -> {
                c.options.guiScale().set(2); c.resizeGui();
                c.getLanguageManager().setSelected("tr_tr"); c.options.languageCode = "tr_tr"; reload.set(c.reloadResourcePacks());
            });
            context.waitFor(c -> reload.get().isDone() && c.gui.overlay() == null, 400);
            context.setScreen(() -> new PiggyModMenu().getModConfigScreenFactory().create(null));
            context.runOnClient(c -> {
                check(c.gui.screen() instanceof PiggyConfigScreen, "Mod Menu did not open the grid");
                check(c.gui.screen().children().stream().noneMatch(EditBox.class::isInstance), "Settings are exposed on the root screen");
            });
            shot(context, "menu-grid-tr-320");
            context.clickScreenButton("piggy.menu.bunny"); shot(context, "menu-bunny-tr-320"); context.clickScreenButton("gui.cancel");
            context.clickScreenButton("piggy.menu.interface"); shot(context, "menu-interface-tr-320");
            context.clickScreenButton("piggy.option.show_hud"); shot(context, "menu-speed-hud-tr-320");
            context.runOnClient(c -> c.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue("75.5"));
            context.clickScreenButton("piggy.save"); context.clickScreenButton("piggy.save");
            context.runOnClient(c -> check(PiggyClient.config.hudX == .755, "Interface save lost child speed HUD edits"));
            context.clickScreenButton("piggy.menu.interactive"); shot(context, "menu-interactive-tr-320"); context.clickScreenButton("gui.cancel");
            context.clickScreenButton("piggy.menu.anti_afk");
            toggle(context, "piggy.quality.anti_afk_jump"); toggle(context, "piggy.quality.anti_afk_crouch");
            shot(context, "menu-anti-afk-wasd-tr-320"); context.clickScreenButton("piggy.save");
            context.runOnClient(c -> check(!QualityClient.config.antiAfkJump && !QualityClient.config.antiAfkCrouch, "WASD-only preferences were not saved"));
            context.clickScreenButton("piggy.menu.free_camera"); shot(context, "menu-free-camera-tr-320"); context.clickScreenButton("gui.cancel");
            context.clickScreenButton("gui.done");
            Vec3 body = context.computeOnClient(c -> c.player.position());
            float yaw = context.computeOnClient(c -> c.player.getYRot());
            context.getInput().pressKey(InputConstants.KEY_F4);
            context.waitFor(c -> FreeCamera.active(), 20);
            context.runOnClient(c -> check(c.getCameraEntity() != c.player, "Camera did not detach"));
            Vec3 camera = context.computeOnClient(c -> c.getCameraEntity().position());
            context.getInput().holdKeyFor(o -> o.keyUp, 10);
            context.getInput().holdKeyFor(o -> o.keyJump, 4);
            context.runOnClient(c -> {
                check(c.getCameraEntity().position().distanceToSqr(camera) > 4, "Camera did not move");
                check(c.player.position().distanceToSqr(body) < .0001, "Freecam moved the real body");
                check(!c.player.input.keyPresses.forward() && !c.player.input.keyPresses.jump(), "Camera input reached the player");
            });
            world.getServer().runOnServer(server -> check(server.getPlayerList().getPlayers().getFirst().position().distanceToSqr(body) < .0001, "Camera position reached the server"));
            // 26.3 uses relative SDL deltas while grabbed; TestInput.moveCursor supplies only absolute coordinates.
            context.runOnClient(c -> {
                var mouse = c.mouseHandler;
                check(mouse.isMouseGrabbed(), "Game did not grab the mouse");
                mouse.onMove(c.getWindow().handle(), mouse.xpos(), mouse.ypos(), 70, 8);
                mouse.onMove(c.getWindow().handle(), mouse.xpos(), mouse.ypos(), 70, 8);
                mouse.handleAccumulatedMovement();
            });
            context.waitTicks(3);
            context.runOnClient(c -> {
                check(Math.abs(c.player.getYRot() - yaw) < .001, "Mouse rotated the body in freecam");
                check(Math.abs(c.getCameraEntity().getYRot() - yaw) > .01, "Mouse did not rotate the camera");
            });
            context.takeScreenshot("free-camera-world");
            context.getInput().pressKey(InputConstants.KEY_F4);
            context.waitFor(c -> !FreeCamera.active() && c.getCameraEntity() == c.player, 20);
            context.getInput().holdKeyFor(o -> o.keyUp, 4);
            context.runOnClient(c -> check(c.player.position().distanceToSqr(body) > .01, "F4 off did not restore movement"));
            context.getInput().pressKey(InputConstants.KEY_F4); context.waitFor(c -> FreeCamera.active(), 20);
            world.getServer().runCommand("tp @a 4 -60 4");
            context.waitFor(c -> !FreeCamera.active() && c.getCameraEntity() == c.player, 40);
            context.getInput().pressKey(InputConstants.KEY_F4); context.waitFor(c -> FreeCamera.active(), 20);
        } finally {
            context.runOnClient(c -> { FreeCamera.stop(); PiggyClient.save(movement); quality.antiAfk = false; QualityClient.save(quality); });
        }
        context.runOnClient(c -> {
            check(!FreeCamera.active(), "Disconnect retained freecam");
            org.slf4j.LoggerFactory.getLogger("Piggy").info("MENU_CAMERA_PASS");
        });
    }
}
