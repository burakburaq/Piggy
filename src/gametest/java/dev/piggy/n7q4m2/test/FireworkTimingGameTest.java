package dev.piggy.n7q4m2.test;

import dev.piggy.n7q4m2.client.quality.QualityClient;
import dev.piggy.n7q4m2.client.quality.placement.UseTiming;
import dev.piggy.n7q4m2.test.mixin.RightClickTestAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Only real held-firework timing, item use results and release behavior. */
public final class FireworkTimingGameTest implements FabricClientGameTest {
    private static final BlockPos LAUNCH = new BlockPos(0,80,2), LEVER = new BlockPos(1,81,1);
    private static void check(boolean c, String text) { if (!c) throw new AssertionError(text); }
    private static void look(Minecraft c, Vec3 point) {
        var delta = point.subtract(c.player.getEyePosition());
        c.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
        c.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        c.player.setOldPosAndRot();
    }
    private static void target(Minecraft c, BlockPos pos) {
        var point=Vec3.atCenterOf(pos).add(0,.5,0);look(c,point);
        c.hitResult=new BlockHitResult(point,Direction.UP,pos,false);
    }
    private static void air(Minecraft c) {
        look(c,c.player.getEyePosition().add(0,0,10));
        c.hitResult=BlockHitResult.miss(c.player.getEyePosition().add(0,0,5),Direction.SOUTH,c.player.blockPosition().south(5));
    }
    private static void equip(ClientGameTestContext ctx, TestServerContext server, int flight, boolean offhand, boolean gliding) {
        server.runOnServer(s->{
            var p=s.getPlayerList().getPlayers().getFirst();p.getInventory().clearContent();
            var rocket=new ItemStack(Items.FIREWORK_ROCKET,32);
            rocket.set(DataComponents.FIREWORKS,new Fireworks(flight,List.of()));
            p.setItemSlot(EquipmentSlot.MAINHAND,offhand?new ItemStack(Items.STICK):rocket);
            p.setItemSlot(EquipmentSlot.OFFHAND,offhand?rocket:ItemStack.EMPTY);
            p.setItemSlot(EquipmentSlot.CHEST,gliding?new ItemStack(Items.ELYTRA):ItemStack.EMPTY);
            p.inventoryMenu.broadcastChanges();
        });
        ctx.waitFor(c->{
            var stack=c.player.getItemInHand(offhand?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND);
            var data=stack.get(DataComponents.FIREWORKS);
            return stack.is(Items.FIREWORK_ROCKET)&&stack.getCount()==32&&data!=null&&data.flightDuration()==flight;
        },80);
        ctx.runOnClient(c->((RightClickTestAccessor)c).piggy$delay(0));
    }
    private static void cadence(ClientGameTestContext ctx, TestServerContext server, int flight, boolean offhand, boolean gliding) {
        int interval=20+flight*10;
        ctx.runOnClient(c->{RightClickProbe.begin();((RightClickTestAccessor)c).piggy$delay(0);});
        long before=ctx.computeOnClient(c->UseTiming.fireworkUses());
        ctx.getInput().holdKeyFor(o->o.keyUse,interval+5);
        ctx.waitTicks(2);
        ctx.runOnClient(c->{
            var ticks=RightClickProbe.finish();
            check(ticks.size()==2&&ticks.get(1)-ticks.get(0)==interval,"Wrong rocket interval flight="+flight+" off="+offhand+" glide="+gliding+": "+ticks);
            check(UseTiming.fireworkUses()-before==2,"Firework use was double counted or did not occur");
            check(((RightClickTestAccessor)c).piggy$delay()==0,"Releasing right click retained rocket delay");
        });
        server.runOnServer(s->{
            var p=s.getPlayerList().getPlayers().getFirst();
            check(p.getItemInHand(offhand?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND).getCount()==30,"Server did not consume exactly two rockets");
        });
    }
    @Override public void runTest(ClientGameTestContext ctx) {
        var old=QualityClient.config.copy();var aim=new AtomicReference<Consumer<Minecraft>>();
        ClientTickEvents.START_CLIENT_TICK.register(c->{var fn=aim.get();if(fn!=null&&c.player!=null&&c.gui.screen()==null)fn.accept(c);});
        try(var world=ctx.worldBuilder().create()) {
            var server=world.getServer();server.runCommand("gamemode survival @a");server.runCommand("difficulty peaceful");
            server.runCommand("fill -2 80 -1 2 80 4 stone");server.runCommand("tp @a 0.5 81 0.5 0 45");
            ctx.waitFor(c->c.player!=null&&c.gui.screen()==null&&c.player.getY()>80,200);
            ctx.runOnClient(c->{QualityClient.config.placementAssist=false;QualityClient.config.autoRestock=false;});
            aim.set(c->target(c,LAUNCH));
            for(int flight=1;flight<=3;flight++)for(boolean offhand:List.of(false,true)) {
                equip(ctx,server,flight,offhand,false);ctx.runOnClient(c->aim.get().accept(c));
                cadence(ctx,server,flight,offhand,false);
            }
            // Holding a rocket does not slow a lever or a failed use in the air.
            equip(ctx,server,3,false,false);aim.set(null);
            server.runOnServer(s->s.getPlayerList().getPlayers().getFirst().level().setBlock(LEVER,
                    Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE,AttachFace.FLOOR),3));
            ctx.waitFor(c->c.level.getBlockState(LEVER).is(Blocks.LEVER),60);
            ctx.runOnClient(c->{
                long serial=UseTiming.fireworkUses();target(c,LEVER);((RightClickTestAccessor)c).piggy$use();
                check(UseTiming.fireworkUses()==serial&&((RightClickTestAccessor)c).piggy$delay()==1,"Lever was classified as a rocket launch");
                air(c);((RightClickTestAccessor)c).piggy$use();
                check(UseTiming.fireworkUses()==serial&&((RightClickTestAccessor)c).piggy$delay()==1,"Failed rocket use received an extended wait");
            });
            // Actual elytra boosts, including offhand, must use the same successful-use classification.
            for(boolean offhand:List.of(false,true)) {
                int flight=offhand?3:1;equip(ctx,server,flight,offhand,true);
                server.runCommand("tp @a 0.5 240 0.5 0 0");
                ctx.waitFor(c->c.player.getY()>200,80);
                server.runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();p.setOnGround(false);p.startFallFlying();});
                ctx.waitFor(c->c.player.isFallFlying(),80);aim.set(FireworkTimingGameTest::air);
                ctx.runOnClient(c->aim.get().accept(c));cadence(ctx,server,flight,offhand,true);aim.set(null);
            }
            ctx.runOnClient(c->org.slf4j.LoggerFactory.getLogger("Piggy").info("FIREWORK_TIMING_PASS"));
        } finally {
            aim.set(null);ctx.getInput().releaseKey(o->o.keyUse);
            ctx.runOnClient(c->{RightClickProbe.finish();QualityClient.config=old;});
        }
    }
}
