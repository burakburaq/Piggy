package dev.piggy.n7q4m2.test;

import dev.piggy.n7q4m2.client.quality.QualityClient;
import dev.piggy.n7q4m2.test.mixin.RightClickTestAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.level.block.state.BlockState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Only hotbar bucket selection, preserving the used stack and authoritative server inventory. */
public final class BucketCycleGameTest implements FabricClientGameTest {
    private static final BlockPos CELL = new BlockPos(0, 81, 2);
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void aim(Minecraft c) {
        Vec3 d = Vec3.atCenterOf(CELL).subtract(c.player.getEyePosition());
        c.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z)));
        c.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))));
        c.player.setOldPosAndRot(); c.hitResult = c.player.pick(c.player.blockInteractionRange(), 1, false);
    }
    private static void fixture(ClientGameTestContext ctx, TestServerContext server, int selected, boolean water, Map<Integer, ItemStack> items) {
        server.runOnServer(s -> {
            var p = s.getPlayerList().getPlayers().getFirst();
            p.level().setBlock(CELL, (water ? Blocks.WATER : Blocks.AIR).defaultBlockState(), 3);
            p.getInventory().clearContent(); p.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            items.forEach((slot, stack) -> p.getInventory().setItem(slot, stack.copy()));
            p.getInventory().setSelectedSlot(selected); p.inventoryMenu.broadcastChanges();
        });
        ctx.runOnClient(c -> c.player.getInventory().setSelectedSlot(selected));
        ctx.waitFor(c -> c.level.getBlockState(CELL).is(water ? Blocks.WATER : Blocks.AIR)
                && items.entrySet().stream().allMatch(e -> ItemStack.matches(c.player.getInventory().getItem(e.getKey()), e.getValue())), 60);
        ctx.runOnClient(c -> { aim(c); ((RightClickTestAccessor)c).piggy$delay(0); });
    }
    private static void use(ClientGameTestContext ctx) { ctx.getInput().pressKey(o -> o.keyUse); ctx.waitTicks(3); }
    private static void selected(ClientGameTestContext ctx, TestServerContext server, int slot) {
        ctx.runOnClient(c -> check(c.player.getInventory().getSelectedSlot() == slot, "Wrong client bucket slot, expected " + slot));
        ctx.waitTicks(2);
        server.runOnServer(s -> check(s.getPlayerList().getPlayers().getFirst().getInventory().getSelectedSlot() == slot, "Bucket selection did not reach the server"));
    }
    @Override public void runTest(ClientGameTestContext ctx) {
        var original = QualityClient.config.copy(); var aiming = new AtomicBoolean();
        var fixtureFluid = new AtomicReference<BlockState>();
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            var state = fixtureFluid.get();
            if (state != null && !s.getPlayerList().getPlayers().isEmpty())
                s.getPlayerList().getPlayers().getFirst().level().setBlock(CELL, state, 3);
        });
        ClientTickEvents.START_CLIENT_TICK.register(c -> { if (aiming.get() && c.player != null && c.gui.screen() == null) aim(c); });
        try (var world = ctx.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode survival @a"); server.runCommand("difficulty peaceful");
            server.runCommand("fill -2 80 -1 2 80 4 stone"); server.runCommand("fill -1 81 1 1 81 3 stone");
            server.runCommand("setblock 0 81 2 air"); server.runCommand("setblock 0 82 0 stone");
            server.runCommand("tp @a 0.5 83 0.5 0 45");
            ctx.waitFor(c -> c.player != null && c.gui.screen() == null && c.player.getY() > 82, 200);
            ctx.runOnClient(c -> { QualityClient.config.bucketCycle = true; QualityClient.config.autoRestock = false; QualityClient.config.placementAssist = false; });
            aiming.set(true);
            fixture(ctx, server, 8, true, Map.of(8, new ItemStack(Items.BUCKET), 0, new ItemStack(Items.BUCKET), 2, new ItemStack(Items.BUCKET)));
            use(ctx); selected(ctx, server, 0);
            server.runOnServer(s -> {
                var inv = s.getPlayerList().getPlayers().getFirst().getInventory();
                check(inv.getItem(8).is(Items.WATER_BUCKET) && inv.getItem(0).is(Items.BUCKET) && inv.getItem(2).is(Items.BUCKET), "Filling moved or lost bucket items");
            });
            fixture(ctx, server, 3, false, Map.of(3, new ItemStack(Items.WATER_BUCKET), 4, new ItemStack(Items.LAVA_BUCKET), 5, new ItemStack(Items.BUCKET), 6, new ItemStack(Items.WATER_BUCKET)));
            use(ctx); selected(ctx, server, 6);
            server.runOnServer(s -> {
                var inv = s.getPlayerList().getPlayers().getFirst().getInventory();
                check(inv.getItem(3).is(Items.BUCKET) && inv.getItem(4).is(Items.LAVA_BUCKET) && inv.getItem(6).is(Items.WATER_BUCKET), "Pouring replaced a bucket or selected another fluid");
            });
            fixture(ctx, server, 1, false, Map.of(1, new ItemStack(Items.LAVA_BUCKET), 2, new ItemStack(Items.WATER_BUCKET), 4, new ItemStack(Items.LAVA_BUCKET)));
            use(ctx); selected(ctx, server, 4);
            fixture(ctx, server, 0, true, Map.of(0, new ItemStack(Items.BUCKET, 3), 1, new ItemStack(Items.BUCKET)));
            use(ctx); selected(ctx, server, 0);
            server.runOnServer(s -> check(s.getPlayerList().getPlayers().getFirst().getInventory().getItem(0).getCount() == 2, "Remaining empty-bucket stack was not retained"));
            // Inventory-only spares must never be pulled into the hotbar or selected.
            fixture(ctx, server, 0, false, Map.of(0, new ItemStack(Items.WATER_BUCKET), 9, new ItemStack(Items.WATER_BUCKET), 1, new ItemStack(Items.LAVA_BUCKET)));
            use(ctx); selected(ctx, server, 0);
            server.runOnServer(s -> check(s.getPlayerList().getPlayers().getFirst().getInventory().getItem(0).is(Items.BUCKET), "No-spare case lost the empty bucket"));
            // A held fill chain must stop with all three full, without pouring the last one back out.
            fixture(ctx, server, 0, true, Map.of(0, new ItemStack(Items.BUCKET), 3, new ItemStack(Items.BUCKET), 6, new ItemStack(Items.BUCKET)));
            fixtureFluid.set(Blocks.WATER.defaultBlockState());
            ctx.getInput().holdKey(o -> o.keyUse);
            ctx.waitFor(c -> c.player.getInventory().getItem(0).is(Items.WATER_BUCKET)
                    && c.player.getInventory().getItem(3).is(Items.WATER_BUCKET)
                    && c.player.getInventory().getItem(6).is(Items.WATER_BUCKET), 80);
            ctx.waitTicks(12);
            ctx.runOnClient(c -> {
                for (int slot : new int[]{0, 3, 6}) check(c.player.getInventory().getItem(slot).is(Items.WATER_BUCKET), "Held fill chain reversed after the last empty bucket");
            });
            // Release, then pour all three; make water available afterwards to catch an unwanted refill.
            ctx.getInput().releaseKey(o -> o.keyUse);
            fixtureFluid.set(Blocks.AIR.defaultBlockState());
            server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().level().setBlock(CELL, Blocks.AIR.defaultBlockState(), 3));
            ctx.waitFor(c -> c.level.getBlockState(CELL).isAir(), 40);
            ctx.runOnClient(c -> ((RightClickTestAccessor)c).piggy$delay(0));
            ctx.getInput().holdKey(o -> o.keyUse);
            ctx.waitFor(c -> c.player.getInventory().getItem(0).is(Items.BUCKET)
                    && c.player.getInventory().getItem(3).is(Items.BUCKET)
                    && c.player.getInventory().getItem(6).is(Items.BUCKET), 80);
            fixtureFluid.set(Blocks.WATER.defaultBlockState());
            ctx.waitTicks(12);
            ctx.runOnClient(c -> {
                for (int slot : new int[]{0, 3, 6}) check(c.player.getInventory().getItem(slot).is(Items.BUCKET), "Held pour chain refilled after the last full bucket");
            });
            ctx.getInput().releaseKey(o -> o.keyUse); ctx.waitTick();
            ctx.runOnClient(c -> ((RightClickTestAccessor)c).piggy$delay(0));
            use(ctx);
            ctx.runOnClient(c -> check(c.player.getInventory().getItem(3).is(Items.WATER_BUCKET), "Releasing use did not unlock the next bucket action"));
            fixtureFluid.set(null);
            ctx.runOnClient(c -> QualityClient.config.bucketCycle = false);
            fixture(ctx, server, 0, true, Map.of(0, new ItemStack(Items.BUCKET), 1, new ItemStack(Items.BUCKET)));
            use(ctx); selected(ctx, server, 0);
            server.runOnServer(s -> check(s.getPlayerList().getPlayers().getFirst().getInventory().getItem(0).is(Items.WATER_BUCKET), "Disabled cycle interfered with filling"));
            ctx.runOnClient(c -> org.slf4j.LoggerFactory.getLogger("Piggy").info("BUCKET_CYCLE_PASS"));
        } finally {
            fixtureFluid.set(null); aiming.set(false); ctx.getInput().releaseKey(o -> o.keyUse);
            ctx.runOnClient(c -> QualityClient.config = original);
        }
    }
}
