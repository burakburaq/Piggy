package dev.piggy.n7q4m2.test;

import dev.piggy.n7q4m2.client.quality.QualityClient;
import dev.piggy.n7q4m2.test.mixin.RightClickTestAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.atomic.AtomicBoolean;

/** Only water-bucket held-use timing, with real filling/pickup in both hands. */
public final class WaterBucketTimingGameTest implements FabricClientGameTest {
    private static final BlockPos WATER = new BlockPos(0, 81, 2);
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void aim(Minecraft c) {
        Vec3 d = Vec3.atCenterOf(WATER).subtract(c.player.getEyePosition());
        c.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z)));
        c.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))));
        c.player.setOldPosAndRot();
        c.hitResult = c.player.pick(c.player.blockInteractionRange(), 1, false);
    }
    @Override public void runTest(ClientGameTestContext ctx) {
        var original = QualityClient.config.copy(); var aiming = new AtomicBoolean();
        ClientTickEvents.START_CLIENT_TICK.register(c -> { if (aiming.get() && c.player != null && c.gui.screen() == null) aim(c); });
        try (var world = ctx.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode survival @a"); server.runCommand("difficulty peaceful");
            server.runCommand("fill -2 80 -1 2 80 4 stone");
            server.runCommand("fill -1 81 1 1 81 3 stone");
            server.runCommand("setblock 0 81 2 air"); server.runCommand("setblock 0 82 0 stone");
            server.runCommand("tp @a 0.5 83 0.5 0 45");
            ctx.waitFor(c -> c.player != null && c.gui.screen() == null && c.player.getY() > 82, 200);
            ctx.runOnClient(c -> { QualityClient.config.placementAssist = false; QualityClient.config.autoRestock = false; QualityClient.config.bucketCycle = false; });
            for (boolean offhand : new boolean[]{false, true}) {
                server.runOnServer(s -> {
                    var p = s.getPlayerList().getPlayers().getFirst();
                    p.level().setBlock(WATER, Blocks.AIR.defaultBlockState(), 3);
                    p.getInventory().clearContent();
                    p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(offhand ? Items.STICK : Items.WATER_BUCKET));
                    p.setItemSlot(EquipmentSlot.OFFHAND, offhand ? new ItemStack(Items.WATER_BUCKET) : ItemStack.EMPTY);
                    p.inventoryMenu.broadcastChanges();
                });
                ctx.waitFor(c -> c.level.getBlockState(WATER).isAir() && (offhand ? c.player.getOffhandItem() : c.player.getMainHandItem()).is(Items.WATER_BUCKET), 60);
                aiming.set(true);
                ctx.runOnClient(c -> { aim(c); ((RightClickTestAccessor)c).piggy$delay(0); RightClickProbe.begin(); });
                ctx.getInput().holdKey(o -> o.keyUse);
                ctx.waitTick();
                ctx.waitFor(c -> (offhand ? c.player.getOffhandItem() : c.player.getMainHandItem()).is(Items.BUCKET), 10);
                ctx.runOnClient(c -> check(((RightClickTestAccessor)c).piggy$delay() > 1, "Water placement was accelerated"));
                ctx.waitFor(c -> (offhand ? c.player.getOffhandItem() : c.player.getMainHandItem()).is(Items.WATER_BUCKET), 10);
                ctx.waitFor(c -> (offhand ? c.player.getOffhandItem() : c.player.getMainHandItem()).is(Items.BUCKET), 10);
                ctx.getInput().releaseKey(o -> o.keyUse); aiming.set(false);
                var ticks = ctx.computeOnClient(c -> RightClickProbe.finish());
                check(ticks.size() >= 3, "Not enough bucket uses: " + ticks);
                for (int i = 1; i < ticks.size(); i++) check(ticks.get(i) - ticks.get(i - 1) == 4, "Bucket cadence differs from four ticks: " + ticks);
                ctx.waitTicks(2);
                server.runOnServer(s -> {
                    var p = s.getPlayerList().getPlayers().getFirst();
                    check(p.level().getBlockState(WATER).is(Blocks.WATER), "Server did not accept the final water placement");
                    check((offhand ? p.getOffhandItem() : p.getMainHandItem()).is(Items.BUCKET), "Server bucket state disagrees");
                });
            }
            ctx.runOnClient(c -> org.slf4j.LoggerFactory.getLogger("Piggy").info("WATER_BUCKET_TIMING_PASS"));
        } finally {
            aiming.set(false); ctx.getInput().releaseKey(o -> o.keyUse);
            ctx.runOnClient(c -> { RightClickProbe.finish(); QualityClient.config = original; });
        }
    }
}
