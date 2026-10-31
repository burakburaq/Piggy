package dev.piggy.n7q4m2.test;

import com.mojang.blaze3d.platform.Window;
import dev.piggy.n7q4m2.client.PiggyModMenu;
import dev.piggy.n7q4m2.client.quality.QualityClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.util.Util;

/** Simulates only focus loss, explicit pause, and the restored vanilla behavior. */
public final class AntiAfkFocusGameTest implements FabricClientGameTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void focus(Minecraft c, boolean focused) {
        try {
            var field = Window.class.getDeclaredField("focused");
            field.setAccessible(true);
            field.setBoolean(c.getWindow(), focused);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void inactivePause(Minecraft c) {
        try {
            var time = Minecraft.class.getDeclaredField("lastActiveTime");
            time.setAccessible(true);
            time.setLong(c, Util.getMillis() - 1000);
            var method = Minecraft.class.getDeclaredMethod("pauseIfInactive");
            method.setAccessible(true);
            method.invoke(c);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    @Override public void runTest(ClientGameTestContext context) {
        var original = QualityClient.config.copy();
        boolean pausePreference = context.computeOnClient(c -> c.options.pauseOnLostFocus);
        check(FabricLoader.getInstance().getModContainer("piggy").orElseThrow()
                .getMetadata().getName().equals("Piggy"), "Mod metadata name not updated");
        context.runOnClient(c -> check(new PiggyModMenu().getModConfigScreenFactory()
                .create(null).getTitle().getString().equals("Piggy"), "Menu title not updated"));
        try (var world = context.worldBuilder().create()) {
            context.waitFor(c -> c.player != null && c.gui.screen() == null, 200);
            context.runOnClient(c -> {
                c.options.pauseOnLostFocus = true;
                QualityClient.config.antiAfk = true;
                focus(c, false);
                inactivePause(c);
                check(c.gui.screen() == null, "Focus loss opened the pause menu during Anti-AFK");
            });
            context.waitFor(c -> {
                var input = c.player.input.keyPresses;
                return input.forward() || input.backward() || input.left() || input.right() || input.jump() || input.shift();
            }, 100);
            context.runOnClient(c -> {
                check(!c.isWindowActive() && c.gui.screen() == null && QualityClient.config.antiAfk,
                        "Anti-AFK stopped in the background");
                check(c.options.pauseOnLostFocus, "Saved pause preference was overwritten");
                c.pauseGame(true);
                check(c.gui.screen() instanceof PauseScreen, "Explicit pause was blocked");
            });
            context.setScreen(() -> null);
            context.runOnClient(c -> {
                QualityClient.config.antiAfk = false;
                focus(c, false);
                inactivePause(c);
                check(c.gui.screen() instanceof PauseScreen, "Normal focus-loss pause was not restored");
            });
        } finally {
            context.runOnClient(c -> {
                focus(c, true);
                c.options.pauseOnLostFocus = pausePreference;
                original.antiAfk = false;
                QualityClient.save(original);
            });
        }
        org.slf4j.LoggerFactory.getLogger("Piggy").info("ANTI_AFK_FOCUS_PASS");
    }
}
