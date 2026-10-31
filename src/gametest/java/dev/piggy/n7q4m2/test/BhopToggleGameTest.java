package dev.piggy.n7q4m2.test;

import dev.piggy.n7q4m2.client.ClientConfig;
import dev.piggy.n7q4m2.client.PiggyClient;
import dev.piggy.n7q4m2.client.HopConfigScreen;
import dev.piggy.n7q4m2.config.ConfigFiles;
import dev.piggy.n7q4m2.movement.HopAccess;
import dev.piggy.n7q4m2.movement.HopController;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Focused check of the persistent bhop menu setting; no other feature scenarios are run. */
public final class BhopToggleGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void menuToggle(ClientGameTestContext context) {
        context.setScreen(() -> new HopConfigScreen(null));
        context.runOnClient(client -> {
            String prefix = Component.translatable("piggy.option.enabled").getString() + ": ";
            client.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(b -> b.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow().onPress(null);
        });
        context.clickScreenButton("piggy.save");
    }
    private static void awaitSetting(ClientGameTestContext context, TestServerContext server, boolean enabled) {
        for (int i = 0; i < 60; i++) {
            boolean serverValue = server.computeOnServer(s -> ((HopAccess)s.getPlayerList().getPlayers().getFirst()).piggy$enabled());
            boolean clientValue = context.computeOnClient(c -> ((HopAccess)c.player).piggy$enabled());
            if (clientValue == enabled && serverValue == enabled) return;
            context.waitTick();
        }
        throw new AssertionError("Menu setting did not reach both client and server: " + enabled);
    }
    @Override public void runTest(ClientGameTestContext context) {
        var original = PiggyClient.config.copy();
        context.runOnClient(client -> PiggyClient.save(new ClientConfig()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode survival @a");
            context.waitFor(c -> c.player != null && c.gui.screen() == null && PiggyClient.supported, 200);
            awaitSetting(context, world.getServer(), false);
            context.getInput().holdKey(options -> options.keyUp);
            context.getInput().holdKeyFor(options -> options.keyJump, 30);
            context.getInput().releaseKey(options -> options.keyUp);
            context.runOnClient(c -> check(!HopController.movementActive(c.player), "Ordinary jumping enabled an off preference"));
            menuToggle(context);
            awaitSetting(context, world.getServer(), true);
            context.runOnClient(c -> check(ConfigFiles.load("piggy-client.json", ClientConfig.class, new ClientConfig()).bhopEnabled,
                    "ON preference was not saved"));
            context.getInput().holdKey(options -> options.keyUp);
            context.getInput().holdKey(options -> options.keyJump);
            context.waitFor(c -> ((HopAccess)c.player).piggy$state().active(), 120);
            context.getInput().releaseKey(options -> options.keyJump);
            context.getInput().holdKey(options -> options.keyShift);
            context.waitTicks(3);
            awaitSetting(context, world.getServer(), true);
            context.runOnClient(c -> check(PiggyClient.config.bhopEnabled && !((HopAccess)c.player).piggy$state().active(),
                    "Crouching changed the menu preference instead of just resetting the chain"));
            context.getInput().releaseKey(options -> options.keyShift);
            context.waitFor(c -> !c.player.isShiftKeyDown(), 20);
            context.runOnClient(c -> check(PiggyClient.config.bhopEnabled && HopController.movementActive(c.player),
                    "Crouching latched the bhop setting off"));
            context.getInput().holdKey(options -> options.keyJump);
            context.waitFor(c -> ((HopAccess)c.player).piggy$state().active(), 120);
            context.getInput().releaseKey(options -> options.keyJump);
            context.getInput().releaseKey(options -> options.keyUp);
            world.getServer().runOnServer(server -> {
                var p = server.getPlayerList().getPlayers().getFirst();
                var a = (HopAccess)p;
                a.piggy$state().setActive(true); a.piggy$state().impactSpeedBps(0);
                p.setHealth(20); p.setInvulnerableTime(0);
                p.causeFallDamage(10, 1, p.damageSources().fall());
                check(!a.piggy$state().active(), "Fall damage did not reset the chain");
                check(a.piggy$enabled() && HopController.movementActive(p), "Fall damage turned the saved bhop mode off");
            });
            context.waitTicks(30);
            awaitSetting(context, world.getServer(), true);
            context.runOnClient(c -> check(PiggyClient.config.bhopEnabled, "Idling disabled bhop"));
            menuToggle(context);
            awaitSetting(context, world.getServer(), false);
            context.runOnClient(c -> {
                check(!((HopAccess)c.player).piggy$state().active(), "OFF left the old chain active");
                check(!ConfigFiles.load("piggy-client.json", ClientConfig.class, new ClientConfig()).bhopEnabled,
                        "OFF preference was not saved");
            });
            context.waitTicks(15);
            awaitSetting(context, world.getServer(), false);
        } finally {
            context.getInput().releaseKey(options -> options.keyJump);
            context.getInput().releaseKey(options -> options.keyUp);
            context.getInput().releaseKey(options -> options.keyShift);
            context.runOnClient(c -> PiggyClient.save(original));
        }
        context.runOnClient(c -> org.slf4j.LoggerFactory.getLogger("Piggy").info("BHOP_TOGGLE_PASS"));
    }
}
