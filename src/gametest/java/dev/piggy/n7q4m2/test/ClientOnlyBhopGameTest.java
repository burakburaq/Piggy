package dev.piggy.n7q4m2.test;

import dev.piggy.n7q4m2.client.ClientConfig;
import dev.piggy.n7q4m2.client.PiggyClient;
import dev.piggy.n7q4m2.client.HopConfigScreen;
import dev.piggy.n7q4m2.movement.HopAccess;
import dev.piggy.n7q4m2.movement.HopController;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** Client physics and saved menu preference with every server Piggy message suppressed. */
public final class ClientOnlyBhopGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void toggle(ClientGameTestContext context) {
        context.setScreen(() -> new HopConfigScreen(null));
        context.runOnClient(client -> {
            String prefix = Component.translatable("piggy.option.enabled").getString() + ": ";
            client.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(b -> b.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow().onPress(null);
        });
        context.clickScreenButton("piggy.save");
    }
    private static void airFixture(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.player.setPos(client.player.getX(), -54, client.player.getZ());
            client.player.setOnGround(false);
            client.player.setYRot(0); client.player.yRotO = 0;
            client.player.setDeltaMovement(new Vec3(.6, 0, 0));
        });
    }
    @Override public void runTest(ClientGameTestContext context) {
        var original = PiggyClient.config.copy();
        System.setProperty("piggy.test.noServerSupport", "true");
        context.runOnClient(client -> PiggyClient.save(new ClientConfig()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode survival @a");
            context.waitFor(c -> c.player != null && c.gui.screen() == null, 200);
            context.waitTicks(10);
            context.runOnClient(c -> {
                check(!PiggyClient.supported, "Fixture received a server policy");
                check(!HopController.movementActive(c.player), "Default-off changed without the menu");
            });
            toggle(context);
            context.runOnClient(c -> check(HopController.movementActive(c.player), "No-server-support mode did not enable bhop"));
            context.getInput().holdKey(options -> options.keyUp);
            context.getInput().holdKey(options -> options.keyJump);
            context.waitFor(c -> ((HopAccess)c.player).piggy$state().active(), 100);
            context.getInput().releaseKey(options -> options.keyJump);
            airFixture(context);
            context.waitTicks(2);
            context.runOnClient(c -> {
                check(c.player.getDeltaMovement().x > .59, "Client bhop did not remove vanilla air drag");
                check(c.player.getDeltaMovement().z > 0, "Client bhop did not apply air acceleration");
                check(!PiggyClient.supported, "Client unexpectedly needed server support");
            });
            world.getServer().runOnServer(server -> {
                var p = server.getPlayerList().getPlayers().getFirst();
                check(!((HopAccess)p).piggy$enabled(), "Client-only mode sent server movement settings");
                check(!((HopAccess)p).piggy$state().active(), "Server supplied the bhop chain in client-only mode");
            });
            context.setScreen(() -> new HopConfigScreen(null));
            context.waitTicks(3);
            context.takeScreenshot("bhop-client-only-settings");
            context.clickScreenButton("gui.cancel");
            toggle(context);
            context.runOnClient(c -> check(!HopController.movementActive(c.player), "OFF preference did not disable client-only bhop"));
            airFixture(context);
            context.waitTicks(2);
            context.runOnClient(c -> check(c.player.getDeltaMovement().x < .56, "OFF did not restore vanilla air drag"));
        } finally {
            System.clearProperty("piggy.test.noServerSupport");
            context.getInput().releaseKey(options -> options.keyUp);
            context.getInput().releaseKey(options -> options.keyJump);
            context.runOnClient(c -> PiggyClient.save(original));
        }
        context.runOnClient(c -> {
            check(!PiggyClient.supported, "Disconnect retained a previous server policy");
            org.slf4j.LoggerFactory.getLogger("Piggy").info("CLIENT_ONLY_BHOP_PASS");
        });
    }
}
