package dev.piggy.n7q4m2.test;

import dev.piggy.n7q4m2.client.quality.QualityClient;
import dev.piggy.n7q4m2.client.quality.hud.*;
import dev.piggy.n7q4m2.client.quality.placement.PlacementAssist;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.resources.Identifier;

/** Repeatable CPU/allocation diagnostics, not an FPS benchmark. Only touched HUD/preview paths. */
public final class OptimizationGameTest implements FabricClientGameTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void refreshPreview(Minecraft c) {
        try {
            var field = PlacementAssist.class.getDeclaredField("nextPreviewNanos"); field.setAccessible(true); field.setLong(null, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        PlacementAssist.updatePreview(c, 1);
    }
    private static final class Graphics extends GuiGraphicsExtractor {
        int scissors, strata;
        boolean auditCrops;
        final java.util.Set<String> crops = new java.util.HashSet<>();
        Graphics(Minecraft c, GuiRenderState state) { super(c, state, 0, 0); }
        @Override public void enableScissor(int l, int t, int r, int b) { scissors++; super.enableScissor(l,t,r,b); }
        @Override public void nextStratum() { strata++; super.nextStratum(); }
        @Override public void blit(RenderPipeline p, Identifier texture, int x, int y, float u, float v,
                                   int width, int height, int tw, int th, int color) {
            if (auditCrops && texture.getNamespace().equals("piggy")) crops.add(texture.getPath() + ":" + (int)u + ":" + width);
            super.blit(p, texture, x,y,u,v,width,height,tw,th,color);
        }
    }
    @Override public void runTest(ClientGameTestContext ctx) {
        var old = QualityClient.config.copy();
        try (var world = ctx.worldBuilder().create()) {
            ctx.waitFor(c -> c.player != null && c.gui.screen() == null, 200);
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("gamerule natural_health_regeneration false");
            world.getServer().runOnServer(s -> {
                var p = s.getPlayerList().getPlayers().getFirst();
                p.getInventory().clearContent(); p.getInventory().setItem(0, new ItemStack(Items.APPLE, 8));
                p.getFoodData().setFoodLevel(15); p.getFoodData().setSaturation(2.5f); p.setHealth(12);
                p.inventoryMenu.broadcastChanges();
            });
            ctx.runOnClient(c -> { QualityClient.config.foodInfo = true; QualityClient.config.arrowCounter = true;
                QualityClient.config.simpleDebug = true; QualityClient.config.placementAssist = true;
                QualityClient.sync(); c.debugEntries.setOverlayVisible(true); });
            ctx.waitFor(c -> c.player.getMainHandItem().is(Items.APPLE) && ClientMetrics.saturation() == 2.5f
                    && c.player.getFoodData().getFoodLevel() == 15, 200);
            ctx.runOnClient(c -> {
                for (var field : c.gui.hud.getClass().getDeclaredFields())
                    check(!field.getName().startsWith("piggy$") || !net.minecraft.world.entity.player.Player.class.isAssignableFrom(field.getType()),
                            "HUD retains a player reference: " + field.getName());
                var buffer = new FoodHud.Cells(); var reused = buffer.append(1,2,3);
                buffer.clear(); check(buffer.append(4,5,6) == reused && reused.index() == 4 && reused.x() == 5 && reused.y() == 6,
                        "HUD cell buffer does not reuse/update coordinates");
                var state = new GuiRenderState(); var g = new Graphics(c, state);
                var cells = new ArrayList<FoodHud.Cell>();
                for (int i = 0; i < 10; i++) cells.add(new FoodHud.Cell(i, 100 + i * 8, 100));
                Runnable food = () -> {
                    for (var cell : cells) FoodHud.renderHungerCell(g, c.player, cell);
                    FoodHud.renderFood(g, c.player, cells); FoodHud.renderHearts(g, c.player, cells); state.reset();
                };
                PerformanceProbe.sample("food HUD extraction", food);
                g.scissors = 0; food.run();
                System.out.println("FLOWHOP_COUNTS food scissors=" + g.scissors);
                check(g.scissors == 0, "Food overlays still split draws using per-cell scissors");
                g.auditCrops = true; food.run(); g.auditCrops = false;
                check(g.crops.contains("textures/gui/hunger_gain.png:4:5"), "Odd hunger must use the right half");
                check(g.crops.contains("textures/gui/saturation_outline.png:7:2"), "Fractional saturation crop incorrect");
                var prediction = FoodHud.prediction(c.player, c.player.getMainHandItem());
                check(prediction == FoodHud.prediction(c.player, c.player.getMainHandItem()), "Unchanged food prediction was reallocated");
                check(prediction.hunger() == 19, "Initial food prediction incorrect");
                c.player.getFoodData().setFoodLevel(13);
                check(FoodHud.prediction(c.player, c.player.getMainHandItem()).hunger() == 17, "Food cache ignored hunger change");
                c.player.getFoodData().setFoodLevel(15);
                var carrot = new ItemStack(Items.CARROT);
                check(FoodHud.prediction(c.player, carrot).hunger() == 18, "Food cache ignored held food change");
                c.player.getInventory().setItem(0, new ItemStack(Items.GOLDEN_APPLE));
                g.auditCrops = true; FoodHud.renderHearts(g, c.player, cells); g.auditCrops = false; state.reset();
                check(g.crops.stream().anyMatch(s -> s.startsWith("textures/gui/health_gain.png:")), "Heart preview disappeared");
                c.player.getInventory().setItem(0, new ItemStack(Items.APPLE, 8));
                PerformanceProbe.sample("F3 rebuild and width", () -> { SimpleDebug.invalidate(); SimpleDebug.bounds(g.guiWidth()); });
                int oldTick = c.player.tickCount;
                try { PerformanceProbe.sample("F3 unchanged next-tick", () -> { c.player.tickCount++; SimpleDebug.bounds(g.guiWidth()); }); }
                finally { c.player.tickCount = oldTick; }
                var before = SimpleDebug.lines();
                double oldX = c.player.getX();
                c.player.setPos(oldX + .2, c.player.getY(), c.player.getZ()); c.player.tickCount++;
                var after = SimpleDebug.lines();
                check(before.get(0) == after.get(0) && before.get(15) == after.get(15), "Unchanged F3 title/biome rows were rebuilt");
                check(!before.get(11).getString().equals(after.get(11).getString()), "F3 position cache did not update");
                c.player.setPos(oldX, c.player.getY(), c.player.getZ()); c.player.tickCount = oldTick;
            });
            ctx.waitTicks(2); ctx.takeScreenshot("optimized-food-odd-hunger");
            ctx.runOnClient(c -> {
                c.debugEntries.setOverlayVisible(false);
                c.player.getInventory().clearContent(); c.player.getInventory().setItem(0, new ItemStack(Items.BOW));
                for (int slot = 1; slot < 36; slot++) {
                    var stack = new ItemStack(slot % 2 == 0 ? Items.TIPPED_ARROW : Items.ARROW, slot);
                    if (slot % 2 == 0) stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.POISON));
                    c.player.getInventory().setItem(slot, stack);
                }
                var state = new GuiRenderState(); var g = new Graphics(c, state);
                Runnable counters = () -> { HudFeatures.render(g, c.getDeltaTracker()); state.reset(); };
                PerformanceProbe.sample("35-group arrow HUD extraction", counters);
                g.strata = 0; counters.run();
                System.out.println("FLOWHOP_COUNTS arrow strata=" + g.strata);
                check(g.strata == 1, "Arrow count rendering creates more than one upper layer");
            });
            ctx.waitTicks(2); ctx.takeScreenshot("optimized-arrow-layers");
            ctx.runOnClient(c -> {
                c.player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE));
                for (int z = -2; z <= 2; z++) c.level.setBlock(new BlockPos(0,80,z), Blocks.STONE.defaultBlockState(), 3);
                c.player.setPos(.5, 81, 1.5); c.player.setYRot(0); c.player.setXRot(44); c.player.setOldPosAndRot();
                c.hitResult = BlockHitResult.miss(new Vec3(.5, 80.8, 3.4), net.minecraft.core.Direction.UP, new BlockPos(0,80,3));
                var target = PlacementAssist.findTarget(c, 1);
                check(target != null, "Bridge preview fixture missing");
                PerformanceProbe.sample("preview updates", () -> PlacementAssist.updatePreview(c, 1));
                refreshPreview(c); var cached = PlacementAssist.preview(); refreshPreview(c);
                check(cached != null && cached == PlacementAssist.preview(), "Unchanged preview was recomputed within the tick");
                c.level.setBlock(cached.position(), Blocks.COBBLESTONE.defaultBlockState(), 3); refreshPreview(c);
                check(PlacementAssist.preview() == null || !PlacementAssist.preview().position().equals(cached.position()), "Block update retained a stale preview");
                c.level.setBlock(cached.position(), Blocks.AIR.defaultBlockState(), 3); refreshPreview(c);
                check(PlacementAssist.preview() != null && PlacementAssist.preview().position().equals(cached.position()), "Removing a block did not refresh preview");
                check(PlacementAssist.findTarget(c,1).position().equals(target.position()), "Use target changed");
                c.player.setYRot(180); c.player.setOldPosAndRot();
                var turned = PlacementAssist.findTarget(c,1);
                check(turned == null || !turned.position().equals(target.position()), "Actual use reused a stale preview after turning");
                QualityClient.config.placementAssist = false; PlacementAssist.updatePreview(c, 1);
                check(PlacementAssist.preview() == null, "Disabled preview remains visible");
                org.slf4j.LoggerFactory.getLogger("Piggy").info("OPTIMIZATION_PASS");
            });
        } finally {
            ctx.runOnClient(c -> { QualityClient.config = old; c.debugEntries.setOverlayVisible(false); FoodHud.invalidate(); });
        }
    }
}
