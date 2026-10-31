package dev.piggy.n7q4m2.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.piggy.n7q4m2.client.quality.QualityClient;
import dev.piggy.n7q4m2.client.quality.QualityConfigScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Options;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.Function;

/** Only exercises Anti-AFK: menu, automatic input, mouse independence and manual cancellation. */
public final class AntiAfkGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void enable(ClientGameTestContext context) {
        context.runOnClient(c -> { var next = QualityClient.config.copy(); next.antiAfk = true; QualityClient.save(next); });
    }
    @Override public void runTest(ClientGameTestContext context) {
        var original = QualityClient.config.copy();
        context.runOnClient(c -> QualityClient.config.antiAfk = false);
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode survival @a");
            context.waitFor(c -> c.player != null && c.gui.screen() == null, 200);
            context.setScreen(() -> new QualityConfigScreen(null, QualityConfigScreen.Section.ANTI_AFK));
            context.runOnClient(c -> {
                String label = Component.translatable("piggy.quality.anti_afk").getString() + ": ";
                c.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(b -> b.getMessage().getString().startsWith(label)).findFirst().orElseThrow().onPress(null);
            });
            context.waitTicks(2);
            context.takeScreenshot("anti-afk-settings");
            context.clickScreenButton("piggy.save");
            context.waitFor(c -> {
                var i = c.player.input.keyPresses;
                return i.forward() || i.backward() || i.left() || i.right() || i.shift() || i.jump();
            }, 100);
            context.waitTicks(50);
            context.runOnClient(c -> {
                check(QualityClient.config.antiAfk, "Automatic movement cancelled itself");
                check(!c.options.keyUp.isDown() && !c.options.keyDown.isDown() && !c.options.keyLeft.isDown()
                        && !c.options.keyRight.isDown() && !c.options.keyJump.isDown() && !c.options.keyShift.isDown(),
                        "Anti-AFK modified the physical key state");
            });
            context.getInput().moveCursor(12, 5);
            context.getInput().lookAt(35, 10);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitTicks(2);
            context.runOnClient(c -> check(QualityClient.config.antiAfk, "Mouse movement or clicks cancelled Anti-AFK"));
            List<Function<Options, KeyMapping>> keys = List.of(o -> o.keyUp, o -> o.keyLeft, o -> o.keyDown,
                    o -> o.keyRight, o -> o.keyShift, o -> o.keyJump);
            for (var key : keys) {
                enable(context);
                context.getInput().pressKey(key);
                context.runOnClient(c -> check(!QualityClient.config.antiAfk, "Manual movement/sneak/jump did not cancel Anti-AFK"));
                context.getInput().releaseKey(key);
                context.waitTicks(2);
                context.runOnClient(c -> {
                    var i = c.player.input.keyPresses;
                    check(!i.forward() && !i.backward() && !i.left() && !i.right() && !i.jump() && !i.shift(),
                            "Cancelled Anti-AFK left input stuck down");
                });
            }
            enable(context);
            context.setScreen(() -> new QualityConfigScreen(null, QualityConfigScreen.Section.ANTI_AFK));
            context.getInput().pressKey(o -> o.keyUp);
            context.runOnClient(c -> check(!QualityClient.config.antiAfk, "Manual key in the menu did not cancel Anti-AFK"));
            context.clickScreenButton("piggy.save");
            context.runOnClient(c -> check(!QualityClient.config.antiAfk, "Saving an old menu restarted cancelled Anti-AFK"));
            enable(context);
        } finally {
            context.runOnClient(c -> { original.antiAfk = false; QualityClient.save(original); });
        }
        context.runOnClient(c -> {
            check(!QualityClient.config.antiAfk, "Anti-AFK remained active after disconnect");
            org.slf4j.LoggerFactory.getLogger("Piggy").info("ANTI_AFK_PASS");
        });
    }
}
