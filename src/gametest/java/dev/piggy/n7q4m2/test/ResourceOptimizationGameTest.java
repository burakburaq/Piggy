package dev.piggy.n7q4m2.test;

import dev.piggy.n7q4m2.client.ClientConfig;
import dev.piggy.n7q4m2.client.SpeedHud;
import dev.piggy.n7q4m2.client.quality.QualityClient;
import dev.piggy.n7q4m2.client.quality.hud.AmmoGroups;
import dev.piggy.n7q4m2.client.quality.hud.HudFeatures;
import dev.piggy.n7q4m2.client.quality.hud.SimpleDebug;
import dev.piggy.n7q4m2.client.quality.inventory.InventoryFeatures;
import dev.piggy.n7q4m2.quality.metrics.FrameTimes;
import dev.piggy.n7q4m2.quality.metrics.HudLayout;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.Fireworks;
import java.util.ArrayList;
import java.util.List;

/** Isolates data/layout work from Minecraft's item/text rendering allocations. */
public final class ResourceOptimizationGameTest implements FabricClientGameTest {
    private static volatile Object objectSink;
    private static volatile double numberSink;
    private static void check(boolean c, String text) { if (!c) throw new AssertionError(text); }
    private static final class Graphics extends GuiGraphicsExtractor {
        boolean capture;
        int textX, textY;
        final List<ItemStack> icons = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        Graphics(Minecraft c) { super(c,new GuiRenderState(),0,0); }
        void clear() { icons.clear(); labels.clear(); }
        @Override public void item(ItemStack item, int x, int y) { if (capture) icons.add(item); }
        @Override public void text(Font font, String text, int x, int y, int color, boolean shadow) {
            textX=x;textY=y;if (capture) labels.add(text);
        }
        @Override public void nextStratum() {}
    }
    private static void arrows(Minecraft c, Graphics g) {
        g.clear(); g.capture=true; HudFeatures.render(g,c.getDeltaTracker()); g.capture=false;
        var expected=AmmoGroups.arrows(c.player,c.player.getOffhandItem().is(Items.BOW)?c.player.getOffhandItem():c.player.getMainHandItem());
        check(g.icons.size()==expected.size(),"Arrow group count changed");
        for(int i=0;i<expected.size();i++) {
            check(ItemStack.isSameItemSameComponents(g.icons.get(i),expected.get(i).representative()),"Arrow order/components stale at "+i);
            check(g.labels.get(i).equals(Integer.toString(expected.get(i).count())),"Arrow count stale at "+i);
        }
    }
    private static void previewCached(boolean expected) {
        try {
            var container=InventoryFeatures.class.getDeclaredField("cachedContainer");container.setAccessible(true);
            check((container.get(null)!=null)==expected,"Shulker container retained after preview ended");
            if (!expected) {
                var contents=InventoryFeatures.class.getDeclaredField("CONTENTS");contents.setAccessible(true);
                for(var stack:(List<?>)contents.get(null)) check(((ItemStack)stack).isEmpty(),"Shulker contents retained after preview ended");
            }
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    @Override public void runTest(ClientGameTestContext ctx) {
        var old=QualityClient.config.copy();
        try(var world=ctx.worldBuilder().create()) {
            ctx.waitFor(c->c.player!=null&&c.gui.screen()==null,200);
            ctx.runOnClient(c->{
                QualityClient.config.arrowCounter=true; QualityClient.config.rocketCounter=true; QualityClient.config.simpleDebug=true;
                QualityClient.config.shulkerPreview=true;
                c.debugEntries.setOverlayVisible(false);
                c.player.getInventory().clearContent(); c.player.getInventory().setSelectedSlot(0);
                c.player.getInventory().setItem(0,new ItemStack(Items.BOW));
                for(int slot=1;slot<36;slot++) {
                    var stack=new ItemStack(slot%2==0?Items.TIPPED_ARROW:Items.ARROW,16);
                    if(slot%2==0) stack.set(DataComponents.POTION_CONTENTS,new PotionContents(Potions.POISON));
                    c.player.getInventory().setItem(slot,stack);
                }
                var g=new Graphics(c); int originalTick=c.player.tickCount;
                try {
                    PerformanceProbe.sample("unchanged ammo next-tick data/layout",()->{c.player.tickCount++;HudFeatures.render(g,c.getDeltaTracker());});
                    arrows(c,g);
                    var stableBounds=HudFeatures.bounds();c.player.tickCount++;arrows(c,g);
                    check(stableBounds==HudFeatures.bounds(),"Unchanged ammunition recreated its layout");
                    c.player.getInventory().getItem(1).shrink(1); c.player.tickCount++; arrows(c,g);
                    c.player.getInventory().getItem(2).set(DataComponents.POTION_CONTENTS,new PotionContents(Potions.REGENERATION));
                    c.player.tickCount++; arrows(c,g);
                    c.player.getInventory().setItem(2,c.player.getInventory().getItem(2).copy()); c.player.tickCount++; arrows(c,g);
                    c.player.getInventory().setItem(40,new ItemStack(Items.BOW));
                    c.player.getInventory().setSelectedSlot(3); c.player.tickCount++; arrows(c,g);

                    c.player.getInventory().clearContent(); c.player.getInventory().setSelectedSlot(0);
                    c.player.getInventory().setItem(0,new ItemStack(Items.STICK));
                    for(int i=1;i<=3;i++) {
                        var rocket=new ItemStack(Items.FIREWORK_ROCKET,i*5);
                        rocket.set(DataComponents.FIREWORKS,new Fireworks(i,List.of()));
                        c.player.getInventory().setItem(i,rocket);
                    }
                    c.player.getInventory().setItem(40,c.player.getInventory().getItem(2).copy());
                    c.player.startFallFlying(); c.player.tickCount++;
                    g.capture=true;g.clear();HudFeatures.render(g,c.getDeltaTracker());g.capture=false;
                    check(g.labels.equals(List.of("5","20","15")),"Rocket level/offhand totals incorrect: "+g.labels);
                    c.player.getInventory().getItem(2).shrink(1);c.player.tickCount++;
                    g.capture=true;g.clear();HudFeatures.render(g,c.getDeltaTracker());g.capture=false;
                    check(g.labels.equals(List.of("5","19","15")),"Rocket count did not update");
                    c.player.stopFallFlying(); c.debugEntries.setOverlayVisible(true);
                    var settings=new ClientConfig(); settings.hudX=0;settings.hudY=0;
                    PerformanceProbe.sample("speed HUD overlapping F3 layout",()->SpeedHud.draw(g,settings,12.3,false));
                    var speedBox=new HudLayout.Rectangle(g.textX-3,g.textY-3,c.font.width("12.3 b/s")+6,15);
                    check(!speedBox.overlaps(SimpleDebug.bounds(g.guiWidth())),"Cached speed label overlaps F3");
                    c.debugEntries.setOverlayVisible(false);HudFeatures.render(g,c.getDeltaTracker());
                    SpeedHud.draw(g,settings,12.3,false);
                    check(g.textX==4&&g.textY==4,"Speed placement did not update when F3/counters disappeared");
                    settings.hudX=1;settings.hudY=1;SpeedHud.draw(g,settings,12.3,false);
                    check(g.textX>g.guiWidth()/2&&g.textY>g.guiHeight()/2,"Edited speed label position was cached incorrectly");
                    PerformanceProbe.sample("unchanged movement settings",()->objectSink=settings.movement());
                } finally { c.player.tickCount=originalTick; }

                var frames=new FrameTimes();long now=1;frames.frame(now);
                for(int i=0;i<8192;i++)frames.frame(now+=1_000_000L+(i%101)*1000L);
                long end=now;
                PerformanceProbe.sample("1% low 8192 samples",()->numberSink=frames.low(end));
                check(numberSink>0,"Invalid 1% low");
            });
            ctx.setScreen(()->new InventoryScreen(Minecraft.getInstance().player));
            ctx.runOnClient(c->{
                var shulker=new ItemStack(Items.SHULKER_BOX);
                shulker.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND,32))));
                check(InventoryFeatures.renderShulker(new Graphics(c),shulker,100,100),"Shulker preview missing");previewCached(true);
                InventoryFeatures.renderShulker(new Graphics(c),new ItemStack(Items.STICK),100,100);previewCached(false);
                InventoryFeatures.renderShulker(new Graphics(c),shulker,100,100);previewCached(true);
            });
            ctx.setScreen(()->null);
            ctx.runOnClient(c->{previewCached(false);org.slf4j.LoggerFactory.getLogger("Piggy").info("RESOURCE_OPTIMIZATION_PASS");});
        } finally { ctx.runOnClient(c->{QualityClient.config=old;c.debugEntries.setOverlayVisible(false);SimpleDebug.invalidate();objectSink=null;}); }
    }
}
