package dev.piggy.n7q4m2.test;

import dev.piggy.n7q4m2.client.PiggyClient;
import dev.piggy.n7q4m2.client.HopConfigScreen;
import dev.piggy.n7q4m2.client.quality.QualityConfigScreen;
import dev.piggy.n7q4m2.movement.HopAccess;
import com.terraformersmc.modmenu.gui.ModsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.SharedConstants;
import com.mojang.blaze3d.platform.InputConstants;

/** Only checks the version migration: startup, common/client hooks and extracted GUI rendering. */
public final class MigrationSmokeGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        if (!SharedConstants.getCurrentVersion().name().equals("26.3")) throw new AssertionError("Wrong Minecraft version");
        context.setScreen(() -> new ModsScreen(null));
        context.waitTicks(5);
        context.takeScreenshot("26.3-mod-menu");
        context.setScreen(() -> new HopConfigScreen(null));
        context.waitTicks(5);
        context.takeScreenshot("26.3-movement-settings");
        context.setScreen(() -> new QualityConfigScreen(null));
        context.waitTicks(5);
        context.takeScreenshot("26.3-quality-settings");
        context.setScreen(() -> null);
        try (var world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.gui.screen() == null && PiggyClient.supported, 400);
            context.runOnClient(client -> {
                if (!(client.player instanceof HopAccess)) throw new AssertionError("Movement hooks missing");
            });
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("give @a minecraft:apple");
            context.getInput().pressKey(InputConstants.KEY_F3);
            context.waitTicks(12);
            context.takeScreenshot("26.3-world-hud");
            double startZ = context.computeOnClient(client -> client.player.getZ());
            context.getInput().holdKeyFor(options -> options.keyUp, 10);
            context.runOnClient(client -> {
                if (client.player.getZ() <= startZ + .5) throw new AssertionError("26.3 movement packets were rejected");
            });
            world.getServer().runCommand("fill 0 80 -2 0 80 2 minecraft:stone");
            world.getServer().runCommand("give @a minecraft:cobblestone 16");
            world.getServer().runCommand("tp @a 0.5 81 1.5 0 44");
            context.runOnClient(client -> client.player.getInventory().setSelectedSlot(1));
            context.waitFor(client -> client.player.getY() > 80.9 && client.player.getMainHandItem().is(net.minecraft.world.item.Items.COBBLESTONE)
                    && client.level.getBlockState(new net.minecraft.core.BlockPos(0,80,2)).is(net.minecraft.world.level.block.Blocks.STONE), 100);
            context.waitFor(client -> dev.piggy.n7q4m2.client.quality.placement.PlacementAssist.preview() != null, 100);
            context.waitTicks(5);
            context.takeScreenshot("26.3-bridge-submit");

        }
        context.runOnClient(client -> org.slf4j.LoggerFactory.getLogger("Piggy").info("MIGRATION_26_3_PASS"));
    }
}
