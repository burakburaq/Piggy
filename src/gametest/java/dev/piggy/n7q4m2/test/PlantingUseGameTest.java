package dev.piggy.n7q4m2.test;

import dev.piggy.n7q4m2.client.quality.QualityClient;
import dev.piggy.n7q4m2.test.mixin.RightClickTestAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Actual held-use input, crop placement packets and server/client eating state, both hands. */
public final class PlantingUseGameTest implements FabricClientGameTest {
    private static final BlockPos FIRST = new BlockPos(0,80,2), SECOND = FIRST.south();
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void look(Minecraft c, Vec3 point) {
        var delta = point.subtract(c.player.getEyePosition());
        c.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        c.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        c.player.setOldPosAndRot();
    }
    private static void soil(Minecraft c, BlockPos pos) {
        var point = Vec3.atCenterOf(pos).add(0,.5,0);
        look(c,point); c.hitResult = new BlockHitResult(point, Direction.UP, pos, false);
    }
    private static void air(Minecraft c) {
        look(c,c.player.getEyePosition().add(0,5,0));
        c.hitResult = c.player.pick(c.player.blockInteractionRange(),1,false);
    }
    @Override public void runTest(ClientGameTestContext ctx) {
        var old = QualityClient.config.copy();
        var aim = new AtomicReference<Consumer<Minecraft>>();
        ClientTickEvents.START_CLIENT_TICK.register(c -> {
            var action = aim.get(); if (action != null && c.player != null && c.gui.screen() == null) action.accept(c);
        });
        try (var world = ctx.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode survival @a"); server.runCommand("difficulty peaceful");
            server.runCommand("fill -2 80 -1 2 80 4 stone"); server.runCommand("tp @a 0.5 81 0.5 0 45");
            ctx.waitFor(c -> c.player != null && c.gui.screen() == null && c.player.getY() > 80,200);
            ctx.runOnClient(c -> { QualityClient.config.placementAssist = false; QualityClient.config.autoRestock = false; });
            for (var item : List.of(Items.CARROT, Items.POTATO)) for (boolean offhand : List.of(false,true)) {
                server.runOnServer(s -> {
                    var p = s.getPlayerList().getPlayers().getFirst();
                    p.getInventory().clearContent(); p.getFoodData().setFoodLevel(10);
                    p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(offhand ? Items.STICK : item,16));
                    p.setItemSlot(EquipmentSlot.OFFHAND, offhand ? new ItemStack(item,16) : ItemStack.EMPTY);
                    for (var pos : List.of(FIRST,SECOND)) {
                        p.level().setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3);
                        p.level().setBlock(pos,Blocks.FARMLAND.defaultBlockState(),3);
                    }
                    p.inventoryMenu.broadcastChanges();
                });
                ctx.waitFor(c -> c.player.getItemInHand(offhand ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND).is(item)
                        && c.player.getFoodData().getFoodLevel() < 20 && c.level.getBlockState(FIRST.above()).isAir(),80);
                var crop = item == Items.CARROT ? Blocks.CARROTS : Blocks.POTATOES;
                aim.set(c -> soil(c,c.level.getBlockState(FIRST.above()).is(crop) ? SECOND : FIRST));
                ctx.runOnClient(c -> { aim.get().accept(c); ((RightClickTestAccessor)c).piggy$delay(0); RightClickProbe.begin(true); });
                ctx.getInput().holdKey(o -> o.keyUse);
                ctx.waitFor(c -> c.level.getBlockState(FIRST.above()).is(crop) && c.level.getBlockState(SECOND.above()).is(crop),60);
                ctx.waitTicks(5);
                ctx.runOnClient(c -> {
                    check(!c.player.isUsingItem(), "Occupied soil started eating: " + item + " offhand=" + offhand);
                    var ticks = RightClickProbe.finish();
                    check(ticks.size() == 2 && ticks.get(1)-ticks.get(0)==2,"Planting cadence changed: " + ticks);
                });
                server.runOnServer(s -> {
                    var p=s.getPlayerList().getPlayers().getFirst();
                    check(!p.isUsingItem(),"Server started eating on occupied soil");
                    check(p.level().getBlockState(FIRST.above()).is(crop) && p.level().getBlockState(SECOND.above()).is(crop),"Server rejected planting");
                });
                aim.set(PlantingUseGameTest::air);
                ctx.waitTicks(8);
                ctx.runOnClient(c -> check(!c.player.isUsingItem(),"Looking away during held planting started eating"));
                server.runOnServer(s -> {
                    var p=s.getPlayerList().getPlayers().getFirst();
                    check(!p.isUsingItem(),"Held planting sent an eating packet to server");
                    check(p.getItemInHand(offhand ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND).getCount()==14,"Unexpected crop consumption");
                });
                ctx.getInput().releaseKey(o -> o.keyUse); ctx.waitTick();
                ctx.runOnClient(c -> ((RightClickTestAccessor)c).piggy$delay(0));
                ctx.getInput().holdKey(o -> o.keyUse); ctx.waitTicks(3);
                ctx.runOnClient(c -> check(c.player.isUsingItem(),"Releasing and pressing again did not restore eating"));
                server.runOnServer(s -> check(s.getPlayerList().getPlayers().getFirst().isUsingItem(),"Normal eating packet not restored"));
                ctx.getInput().releaseKey(o -> o.keyUse); ctx.waitTicks(2);
                aim.set(null);
            }
            ctx.runOnClient(c -> org.slf4j.LoggerFactory.getLogger("Piggy").info("PLANTING_USE_PASS"));
        } finally {
            aim.set(null); ctx.getInput().releaseKey(o -> o.keyUse);
            ctx.runOnClient(c -> { RightClickProbe.finish(); QualityClient.config = old; });
        }
    }
}
