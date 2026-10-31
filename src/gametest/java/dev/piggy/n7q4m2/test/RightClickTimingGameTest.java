package dev.piggy.n7q4m2.test;

import dev.piggy.n7q4m2.client.quality.QualityClient;
import dev.piggy.n7q4m2.test.mixin.RightClickTestAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Targeted held-use cadence checks against the real client input loop and integrated server. */
public final class RightClickTimingGameTest implements FabricClientGameTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void look(Minecraft c, Vec3 point) {
        Vec3 delta = point.subtract(c.player.getEyePosition());
        c.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        c.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        c.player.setOldPosAndRot();
    }
    private static void target(Minecraft c, BlockPos pos, Direction face) {
        Vec3 point = Vec3.atCenterOf(pos).add(face.getStepX() * .5, face.getStepY() * .5, face.getStepZ() * .5);
        look(c, point); c.hitResult = new BlockHitResult(point, face, pos, false);
    }
    private static void equip(ClientGameTestContext ctx, TestServerContext server, net.minecraft.world.item.Item main, net.minecraft.world.item.Item off) {
        server.runOnServer(s -> {
            var p = s.getPlayerList().getPlayers().getFirst(); p.getInventory().clearContent();
            p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(main, 32));
            p.setItemSlot(EquipmentSlot.OFFHAND, off == Items.AIR ? ItemStack.EMPTY : new ItemStack(off, 32));
            p.inventoryMenu.broadcastChanges();
        });
        ctx.waitFor(c -> c.player.getMainHandItem().is(main) && (off == Items.AIR ? c.player.getOffhandItem().isEmpty() : c.player.getOffhandItem().is(off)), 60);
        ctx.runOnClient(c -> ((RightClickTestAccessor)c).piggy$delay(0));
    }
    private static void cadence(ClientGameTestContext ctx, int ticks, int interval, int minimum, String label) {
        ctx.runOnClient(c -> { ((RightClickTestAccessor)c).piggy$delay(0); RightClickProbe.begin(interval == 2); });
        ctx.getInput().holdKeyFor(o -> o.keyUse, ticks);
        List<Integer> attempts = ctx.computeOnClient(c -> RightClickProbe.finish());
        check(attempts.size() >= minimum, label + " too few uses: " + attempts);
        for (int i = 1; i < attempts.size(); i++) check(attempts.get(i) - attempts.get(i - 1) == interval, label + " wrong interval: " + attempts);
    }
    @Override public void runTest(ClientGameTestContext ctx) {
        var old = QualityClient.config.copy(); var aiming = new AtomicReference<Consumer<Minecraft>>();
        ClientTickEvents.START_CLIENT_TICK.register(c -> { var fn = aiming.get(); if (fn != null && c.player != null && c.gui.screen() == null) fn.accept(c); });
        try (var world = ctx.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode creative @a"); server.runCommand("difficulty peaceful");
            server.runCommand("fill -3 80 -2 3 80 5 stone"); server.runCommand("tp @a 0.5 81 0.5 0 45");
            ctx.waitFor(c -> c.player != null && c.gui.screen() == null && c.player.getY() > 80, 200);
            ctx.runOnClient(c -> { QualityClient.config.placementAssist = false; QualityClient.config.autoRestock = false; });
            for (var tool : List.of(Items.DIAMOND_HOE, Items.DIAMOND_SHOVEL)) {
                server.runOnServer(s -> {
                    var level = s.getPlayerList().getPlayers().getFirst().level();
                    for (int z = 2; z <= 4; z++) level.setBlock(new BlockPos(0, 80, z), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                });
                ctx.waitFor(c -> c.level.getBlockState(new BlockPos(0, 80, 2)).is(Blocks.GRASS_BLOCK), 60);
                equip(ctx, server, tool, Items.AIR);
                aiming.set(c -> {
                    for (int z = 2; z <= 4; z++) if (c.level.getBlockState(new BlockPos(0, 80, z)).is(Blocks.GRASS_BLOCK)) {
                        target(c, new BlockPos(0, 80, z), Direction.UP); break;
                    }
                });
                ctx.runOnClient(c -> aiming.get().accept(c));
                cadence(ctx, 4, 1, 3, tool.toString()); aiming.set(null); ctx.waitTicks(3);
                server.runOnServer(s -> {
                    var level = s.getPlayerList().getPlayers().getFirst().level();
                    var expected = tool == Items.DIAMOND_HOE ? Blocks.FARMLAND : Blocks.DIRT_PATH;
                    for (int z = 2; z <= 4; z++) check(level.getBlockState(new BlockPos(0, 80, z)).is(expected), "Server did not accept consecutive tool uses");
                });
            }
            // Successful interaction while holding a block must still use one tick.
            BlockPos lever = new BlockPos(1, 81, 1);
            server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().level().setBlock(lever, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR), 3));
            equip(ctx, server, Items.COBBLESTONE, Items.AIR);
            ctx.waitFor(c -> c.level.getBlockState(lever).is(Blocks.LEVER), 60);
            ctx.runOnClient(c -> {
                target(c, lever, Direction.UP); ((RightClickTestAccessor)c).piggy$use();
                check(((RightClickTestAccessor)c).piggy$delay() == 1, "Holding a block incorrectly slowed a lever interaction");
            });
            // Main-hand and offhand placement both retain the two-tick held-use interval.
            for (boolean offhand : List.of(false, true)) {
                server.runOnServer(s -> {
                    var level = s.getPlayerList().getPlayers().getFirst().level();
                    level.setBlock(new BlockPos(0, 82, 4), Blocks.STONE.defaultBlockState(), 3);
                    for (int z = 1; z <= 3; z++) level.setBlock(new BlockPos(0, 82, z), Blocks.AIR.defaultBlockState(), 3);
                });
                equip(ctx, server, offhand ? Items.STICK : Items.COBBLESTONE, offhand ? Items.COBBLESTONE : Items.AIR);
                ctx.waitFor(c -> c.level.getBlockState(new BlockPos(0, 82, 3)).isAir(), 60);
                aiming.set(c -> { look(c, new Vec3(.5, 82.5, 4)); c.hitResult = c.player.pick(c.player.blockInteractionRange(), 1, false); });
                ctx.runOnClient(c -> aiming.get().accept(c));
                cadence(ctx, 4, 2, 2, offhand ? "offhand placement" : "main-hand placement"); aiming.set(null); ctx.waitTicks(3);
                server.runOnServer(s -> {
                    var level = s.getPlayerList().getPlayers().getFirst().level();
                    check(level.getBlockState(new BlockPos(0, 82, 3)).is(Blocks.COBBLESTONE) && level.getBlockState(new BlockPos(0, 82, 2)).is(Blocks.COBBLESTONE), "Server did not accept two placements");
                });
            }
            server.runCommand("fill 0 82 1 0 82 4 air");
            server.runCommand("gamemode survival @a");
            server.runCommand("summon minecraft:cow 0.5 81 2.5 {Age:-24000,NoAI:1b,Invulnerable:1b,Tags:[\"use_timing\"]}");
            equip(ctx, server, Items.WHEAT, Items.AIR);
            ctx.waitFor(c -> { for (var e : c.level.entitiesForRendering()) if (e instanceof Cow) return true; return false; }, 60);
            aiming.set(c -> {
                for (var e : c.level.entitiesForRendering()) if (e instanceof Cow) {
                    Vec3 point = e.getBoundingBox().getCenter(); look(c, point); c.hitResult = new EntityHitResult(e, point); break;
                }
            });
            ctx.runOnClient(c -> aiming.get().accept(c));
            cadence(ctx, 4, 1, 3, "feeding"); aiming.set(null); ctx.waitTicks(3);
            server.runOnServer(s -> check(s.getPlayerList().getPlayers().getFirst().getMainHandItem().getCount() <= 29, "Server did not accept repeated feeding"));
            // The retry delay does not shorten continuous eating or consume food twenty times per second.
            server.runCommand("kill @e[type=minecraft:cow,tag=use_timing]"); server.runCommand("gamemode survival @a");
            server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().getFoodData().setFoodLevel(10));
            equip(ctx, server, Items.BREAD, Items.AIR);
            ctx.waitFor(c -> c.player.getFoodData().getFoodLevel() < 20, 60);
            ctx.runOnClient(c -> { look(c, c.player.getEyePosition().add(0, 5, 0)); c.hitResult = c.player.pick(4.5, 1, false); RightClickProbe.begin(); });
            ctx.getInput().holdKey(o -> o.keyUse); ctx.waitTicks(5);
            ctx.runOnClient(c -> {
                check(c.player.isUsingItem() && c.player.getUseItemRemainingTicks() > 20, "Continuous item-use duration was shortened");
                check(RightClickProbe.finish().size() == 1, "Eating restarted on every tick");
            });
            ctx.getInput().releaseKey(o -> o.keyUse);
            ctx.runOnClient(c -> org.slf4j.LoggerFactory.getLogger("Piggy").info("RIGHT_CLICK_TIMING_PASS"));
        } finally {
            aiming.set(null); ctx.getInput().releaseKey(o -> o.keyUse);
            ctx.runOnClient(c -> { RightClickProbe.finish(); QualityClient.config = old; });
        }
    }
}
