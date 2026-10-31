package dev.piggy.n7q4m2.test;

import dev.piggy.n7q4m2.client.PiggyClient;
import dev.piggy.n7q4m2.client.quality.QualityClient;
import dev.piggy.n7q4m2.client.quality.QualityConfig;
import dev.piggy.n7q4m2.client.quality.QualityConfigScreen;
import dev.piggy.n7q4m2.client.quality.hud.AmmoGroups;
import dev.piggy.n7q4m2.client.quality.hud.ClientMetrics;
import dev.piggy.n7q4m2.client.quality.hud.HudFeatures;
import dev.piggy.n7q4m2.client.quality.hud.FoodHud;
import dev.piggy.n7q4m2.client.quality.hud.SimpleDebug;
import dev.piggy.n7q4m2.quality.metrics.HudLayout;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import dev.piggy.n7q4m2.movement.HopAccess;
import dev.piggy.n7q4m2.quality.QualityOfLife;
import dev.piggy.n7q4m2.quality.metrics.FoodPreview;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Live integrated-world tests; this class is excluded from the released mod jar. */
public final class QualityHudGameTest implements FabricClientGameTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static ItemStack poison(int count) {
        ItemStack stack = new ItemStack(Items.TIPPED_ARROW, count);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.POISON));
        return stack;
    }
    private static ItemStack rocket(int flight, int count, boolean explosion) {
        ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET, count);
        stack.set(DataComponents.FIREWORKS, new Fireworks(flight,
                explosion ? List.of(FireworkExplosion.DEFAULT) : List.of()));
        return stack;
    }
    private static void layout(Screen screen) {
        var widgets = screen.children().stream().filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast).toList();
        for (int i = 0; i < widgets.size(); i++) {
            var a = widgets.get(i);
            check(a.getX() >= 0 && a.getY() >= 0 && a.getX() + a.getWidth() <= screen.width
                    && a.getY() + a.getHeight() <= screen.height, "Quality setting extends outside screen");
            for (int j = i + 1; j < widgets.size(); j++) {
                var b = widgets.get(j);
                check(!(a.getX() < b.getX() + b.getWidth() && a.getX() + a.getWidth() > b.getX()
                        && a.getY() < b.getY() + b.getHeight() && a.getY() + a.getHeight() > b.getY()),
                        "Quality settings overlap");
            }
        }
    }
    private static void toggle(Minecraft client, String key) {
        String label = Component.translatable(key).getString() + ": ";
        client.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().startsWith(label)).findFirst().orElseThrow().onPress(null);
    }
    private static void screenshot(ClientGameTestContext context, String name) {
        context.runOnClient(client -> client.gui.toastManager().clear());
        context.waitTicks(2);
        context.takeScreenshot(name);
    }
    private static void language(ClientGameTestContext context, String code) {
        AtomicReference<CompletableFuture<Void>> reload = new AtomicReference<>();
        context.runOnClient(client -> {
            client.getLanguageManager().setSelected(code);
            client.options.languageCode = code;
            reload.set(client.reloadResourcePacks());
        });
        context.waitFor(client -> reload.get().isDone() && client.gui.overlay() == null, 400);
        check(!reload.get().isCompletedExceptionally(), "Language resource reload failed");
    }
    @Override public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("piggy.preparePlaytest")) return;
        var originalMovement = PiggyClient.config.copy();
        String[] originalLanguage = new String[1];
        int[] originalDisplay = new int[3];
        context.runOnClient(client -> {
            originalLanguage[0] = client.getLanguageManager().getSelected();
            originalDisplay[0] = client.getWindow().getWidth();
            originalDisplay[1] = client.getWindow().getHeight();
            originalDisplay[2] = client.options.guiScale().get();
        });
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("time set day");
            world.getServer().runCommand("difficulty normal");
            context.waitFor(client -> client.player != null && PiggyClient.supported && client.gui.screen() == null, 200);
            context.runOnClient(client -> {
                if (client.gui.hud.isHidden()) client.gui.hud.toggle();
                client.options.reducedDebugInfo().set(false);
                client.debugEntries.setOverlayVisible(false);
                QualityClient.save(new QualityConfig());
                PiggyClient.config.bhopEnabled = false;
                PiggyClient.applySettings();
            });
            context.waitTicks(10);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                check(!((HopAccess) player).piggy$settings().enabled(), "Movement toggle was not disabled for independence check");
                check(QualityOfLife.settings(player).foodInfo(), "Quality settings incorrectly depend on movement toggle");
                player.getInventory().clearContent();
                player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                player.getInventory().setSelectedSlot(0);
                player.getInventory().setItem(0, new ItemStack(Items.APPLE, 5));
                player.getFoodData().setFoodLevel(14);
                player.getFoodData().setSaturation(2.5f);
                player.setHealth(10);
                player.inventoryMenu.broadcastChanges();
            });
            context.waitFor(client -> client.player.getMainHandItem().is(Items.APPLE)
                    && Math.abs(ClientMetrics.saturation() - 2.5f) < .01f && ClientMetrics.tps() > 0, 100);
            context.runOnClient(client -> {
                check(Float.isFinite(ClientMetrics.tps()), "TPS is not a finite server measurement");
                check(ClientMetrics.tps() <= 20.01f, "TPS exceeds configured tick rate");
                check(!((HopAccess) client.player).piggy$enabled(), "Movement must remain disabled while QoL HUD works");
                var food = client.player.getMainHandItem().get(DataComponents.FOOD);
                var preview = FoodPreview.afterEating(client.player.getFoodData().getFoodLevel(), ClientMetrics.saturation(),
                        food.nutrition(), food.saturation());
                check(preview.hunger() == 18 && Math.abs(preview.saturation() - 4.9f) < .01f,
                        "Apple prospective hunger/saturation are incorrect");
                check(FoodHud.recovery(client.player, client.player.getMainHandItem(), preview) > 0,
                        "Food cannot preview natural health recovery");
                // A health change or food switch within one frame must invalidate recovery immediately.
                float previousHealth = client.player.getHealth();
                client.player.setHealth(client.player.getMaxHealth());
                check(FoodHud.recovery(client.player, client.player.getMainHandItem(), preview) == 0,
                        "Recovery cache retained healing at full health");
                client.player.setHealth(previousHealth);
                check(FoodHud.recovery(client.player, client.player.getMainHandItem(), preview) > 0,
                        "Recovery cache did not update after damage");
                check(FoodHud.recovery(client.player, new ItemStack(Items.GOLDEN_APPLE), new FoodPreview(17, 5)) > 0,
                        "Recovery cache lost guaranteed food effects when switching foods");
                check(FoodHud.recovery(client.player, client.player.getMainHandItem(), new FoodPreview(17, 5)) == 0,
                        "Recovery cache retained another food's regeneration effect");
                var lines = Screen.getTooltipFromItem(client, client.player.getMainHandItem());
                String expected = Component.translatable("piggy.hud.food_values", 4, "2.4").getString();
                check(lines.stream().anyMatch(line -> line.getString().equals(expected)), "Food tooltip omits nutritional values");
            });
            screenshot(context, "quality-food-preview-movement-off");
            world.getServer().runCommand("gamerule natural_health_regeneration false");
            context.waitFor(client -> !ClientMetrics.naturalRegeneration(), 100);
            context.runOnClient(client -> check(FoodHud.recovery(client.player, client.player.getMainHandItem(),
                    FoodHud.prediction(client.player, client.player.getMainHandItem())) == 0,
                    "Health preview ignores disabled natural regeneration"));
            screenshot(context, "quality-food-no-natural-regeneration");
            world.getServer().runCommand("gamerule natural_health_regeneration true");
            context.waitFor(client -> ClientMetrics.naturalRegeneration(), 100);
            world.getServer().runOnServer(server -> {
                var food = server.getPlayerList().getPlayers().getFirst().getFoodData();
                food.setFoodLevel(15); food.setSaturation(.5f);
            });
            context.waitFor(client -> client.player.getFoodData().getFoodLevel() == 15
                    && Math.abs(ClientMetrics.saturation() - .5f) < .01f, 100);
            screenshot(context, "quality-food-right-to-left-odd-hunger-half-saturation");
            // Verify a subsequent server-only saturation update reaches the client.
            world.getServer().runOnServer(server -> {
                var food = server.getPlayerList().getPlayers().getFirst().getFoodData();
                food.setFoodLevel(14); food.setSaturation(7.25f);
            });
            context.waitFor(client -> Math.abs(ClientMetrics.saturation() - 7.25f) < .01f, 100);
            screenshot(context, "quality-food-authoritative-saturation");

            context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            context.waitTicks(2);
            double[] cursor = new double[2];
            context.runOnClient(client -> {
                // Main-hand apple in slot 0: vanilla inventory hotbar coordinates.
                double scale = client.getWindow().getGuiScale();
                cursor[0] = ((client.gui.screen().width - 176) / 2.0 + 16) * scale;
                cursor[1] = ((client.gui.screen().height - 166) / 2.0 + 150) * scale;
            });
            context.getInput().setCursorPos(cursor[0], cursor[1]);
            context.waitTicks(3);
            screenshot(context, "quality-food-inventory-tooltip");
            context.setScreen(() -> null);

            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(Items.BOW));
                player.getInventory().setItem(9, new ItemStack(Items.ARROW, 64));
                player.getInventory().setItem(10, new ItemStack(Items.ARROW, 12));
                player.getInventory().setItem(11, poison(8));
                player.getInventory().setItem(12, new ItemStack(Items.ARROW, 3));
                player.inventoryMenu.broadcastChanges();
            });
            context.waitFor(client -> client.player.getMainHandItem().is(Items.BOW)
                    && client.player.getInventory().getItem(12).getCount() == 3, 100);
            context.runOnClient(client -> {
                var groups = AmmoGroups.arrows(client.player, client.player.getMainHandItem());
                check(groups.stream().map(group -> group.count()).toList().equals(List.of(76, 8, 3)),
                        "Ammo ordering incorrectly combines separated normal arrows");
                check(groups.get(1).representative().is(Items.TIPPED_ARROW), "Poison arrow group lost icon/type");
            });
            screenshot(context, "quality-arrow-ordered-groups");
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setItemSlot(EquipmentSlot.OFFHAND, poison(2));
                player.inventoryMenu.broadcastChanges();
            });
            context.waitFor(client -> client.player.getOffhandItem().getCount() == 2, 100);
            context.runOnClient(client -> {
                var groups = AmmoGroups.arrows(client.player, client.player.getMainHandItem());
                check(groups.stream().map(group -> group.count()).toList().equals(List.of(2, 76, 8, 3)),
                        "Offhand arrows are not first in actual ammo selection order");
            });
            screenshot(context, "quality-arrow-offhand-first");
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                player.getInventory().setItem(0, new ItemStack(Items.STICK));
                player.inventoryMenu.broadcastChanges();
            });
            context.waitFor(client -> client.player.getMainHandItem().is(Items.STICK) && client.player.getOffhandItem().isEmpty(), 100);
            screenshot(context, "quality-arrow-hidden-with-wrong-item");

            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getInventory().clearContent();
                player.getInventory().setItem(0, rocket(1, 5, false));
                player.getInventory().setItem(1, rocket(1, 7, true));
                player.getInventory().setItem(2, rocket(2, 9, false));
                player.getInventory().setItem(3, rocket(3, 11, false));
                player.setItemSlot(EquipmentSlot.OFFHAND, rocket(3, 3, true));
                player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
                player.inventoryMenu.broadcastChanges();
            });
            context.waitFor(client -> client.player.getMainHandItem().is(Items.FIREWORK_ROCKET)
                    && client.player.getInventory().getItem(3).getCount() == 11
                    && client.player.getOffhandItem().getCount() == 3, 100);
            context.runOnClient(client -> {
                var groups = AmmoGroups.rockets(client.player);
                check(groups.stream().map(AmmoGroups.RocketGroup::flight).toList().equals(List.of(1, 2, 3)), "Rocket flight groups incorrect");
                check(groups.stream().map(AmmoGroups.RocketGroup::count).toList().equals(List.of(12, 9, 14)),
                        "Rocket counts must combine explosion differences and include offhand");
                check(!client.player.isFallFlying(), "Ground fixture should not be gliding");
            });
            screenshot(context, "quality-rocket-hidden-on-ground");
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(player.getX(), 120, player.getZ());
                player.setOnGround(false);
                player.setDeltaMovement(0, -.1, .2);
                player.startFallFlying();
            });
            context.waitFor(client -> client.player.isFallFlying(), 100);
            screenshot(context, "quality-rocket-during-glide");
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.stopFallFlying();
                player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                player.getInventory().setItem(0, new ItemStack(Items.STICK));
                player.teleportTo(8.5, -60, 16.5);
                player.setDeltaMovement(Vec3.ZERO);
                player.inventoryMenu.broadcastChanges();
            });
            context.waitFor(client -> !client.player.isFallFlying() && client.player.getY() < 0 && client.gui.screen() == null, 100);
            context.getInput().pressKey(InputConstants.KEY_F3);
            context.waitTicks(3);
            context.runOnClient(client -> check(client.debugEntries.isOverlayVisible(), "Real F3 key did not toggle the debug screen"));
            screenshot(context, "quality-f3-overworld-conversion");
            context.runOnClient(client -> {
                var lines = SimpleDebug.lines().stream().map(Component::getString).toList();
                check(lines.getFirst().equals("Version") && lines.get(2).isEmpty() && lines.get(3).equals("Performance"),
                        "F3 headings/blank lines differ from requested layout");
                check(lines.stream().anyMatch(s -> s.startsWith("Mem: ")) && lines.stream().anyMatch(s -> s.startsWith("Chunk in: ")),
                        "F3 memory/chunk details missing");
                check(lines.stream().anyMatch(s -> s.equals("Nether: 1 -60 2")), "F3 conversion omits Y or retains decimals");
                check(lines.stream().anyMatch(s -> s.equals("X 8.5 / Y -60.0 / Z 16.5")), "F3 coordinates are not one decimal");
            });
            context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            context.runOnClient(client -> check(SimpleDebug.lines().isEmpty() && SimpleDebug.bounds(client.gui.screen().width) == null,
                    "F3 remains visible behind inventory"));
            screenshot(context, "quality-f3-hidden-inventory");
            context.setScreen(() -> new PauseScreen(true));
            context.runOnClient(client -> check(SimpleDebug.lines().isEmpty(), "F3 remains visible behind pause menu"));
            screenshot(context, "quality-f3-hidden-pause");
            context.setScreen(() -> new QualityConfigScreen(null));
            context.runOnClient(client -> check(SimpleDebug.lines().isEmpty(), "F3 remains visible behind settings"));
            context.setScreen(() -> null);
            context.getInput().pressKey(InputConstants.KEY_F1);
            context.waitTicks(2);
            context.runOnClient(client -> check(client.gui.hud.isHidden(), "F1 did not hide HUD"));
            screenshot(context, "quality-f1-hides-all-hud");
            context.getInput().pressKey(InputConstants.KEY_F1);
            world.getServer().runCommand("gamerule reduced_debug_info true");
            context.waitFor(Minecraft::showOnlyReducedInfo, 100);
            screenshot(context, "quality-f3-reduced-debug-policy");
            world.getServer().runCommand("gamerule reduced_debug_info false");
            context.waitFor(client -> !client.showOnlyReducedInfo(), 100);
            world.getServer().runOnServer(server -> {
                var nether = server.getLevel(Level.NETHER);
                nether.getChunk(0, 1);
                for (int x = 7; x <= 9; x++) for (int z = 15; z <= 17; z++) {
                    nether.setBlock(new BlockPos(x, 80, z), Blocks.BEDROCK.defaultBlockState(), 3);
                    for (int y = 81; y <= 84; y++) nether.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
                var player = server.getPlayerList().getPlayers().getFirst();
                player.teleport(new TeleportTransition(nether, new Vec3(8.5, 81, 16.5), Vec3.ZERO,
                        0, 0, TeleportTransition.DO_NOTHING));
            });
            context.waitFor(client -> client.level != null && client.level.dimension().equals(Level.NETHER)
                    && client.gui.screen() == null && ClientMetrics.tps() >= 0, 200);
            screenshot(context, "quality-f3-nether-conversion");
            context.runOnClient(client -> check(SimpleDebug.lines().stream().map(Component::getString)
                    .anyMatch(line -> line.equals("Overworld: 68 81 132")), "F3 cache retained overworld position after dimension transfer"));
            context.getInput().pressKey(InputConstants.KEY_F3);
            context.runOnClient(client -> check(!client.debugEntries.isOverlayVisible(), "F3 toggle failed after dimension transfer"));

            context.setScreen(() -> new QualityConfigScreen(null));
            context.waitTicks(2);
            context.runOnClient(client -> { layout(client.gui.screen()); toggle(client, "piggy.quality.arrow_counter"); });
            screenshot(context, "quality-settings-display");
            context.clickScreenButton("gui.cancel");
            context.runOnClient(client -> check(QualityClient.config.arrowCounter, "Cancel changed the stored quality setting"));
            context.setScreen(() -> new QualityConfigScreen(null));
            context.runOnClient(client -> toggle(client, "piggy.quality.arrow_counter"));
            context.clickScreenButton("piggy.save");
            context.waitTicks(10);
            context.runOnClient(client -> check(!QualityClient.config.arrowCounter, "Save did not store changed quality setting"));
            world.getServer().runOnServer(server -> check(!QualityOfLife.settings(server.getPlayerList().getPlayers().getFirst()).arrowCounter(),
                    "Saved quality preference did not reach server"));
            context.setScreen(() -> new QualityConfigScreen(null));
            context.runOnClient(client -> toggle(client, "piggy.quality.arrow_counter"));
            context.clickScreenButton("piggy.save");
            context.setScreen(() -> new QualityConfigScreen(null));
            context.setScreen(() -> new QualityConfigScreen(null, QualityConfigScreen.Section.INTERACTIVE));
            context.runOnClient(client -> layout(client.gui.screen()));
            screenshot(context, "quality-settings-interaction");
            context.clickScreenButton("gui.cancel");

            // Final visual QA: actual Turkish resources at Minecraft's minimum GUI size.
            language(context, "tr_tr");
            context.getInput().resizeWindow(640, 480);
            context.runOnClient(client -> {
                client.options.guiScale().set(2);
                client.resizeGui();
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                check(client.getWindow().getGuiScaledWidth() == 320 && client.getWindow().getGuiScaledHeight() == 240,
                        "Minimum GUI fixture is not 320x240");
                check(Math.abs(client.font.lineHeight * SimpleDebug.FONT_SCALE - 12) < .00001,
                        "F3 font is not fixed at 12 GUI pixels");
                check(Component.translatable("piggy.hud.saturation", "2.5").getString().equals("Doygunluk: 2.5"),
                        "Turkish resources were not loaded");
                check(client.font.width(Component.translatable("piggy.hud.saturation", "2.5 → 4.9")) <= 140,
                        "Turkish food preview text exceeds the 140px food panel");
            });
            context.setScreen(() -> new QualityConfigScreen(null));
            context.runOnClient(client -> layout(client.gui.screen()));
            screenshot(context, "quality-tr-minimum-settings-display");
            context.setScreen(() -> new QualityConfigScreen(null, QualityConfigScreen.Section.INTERACTIVE));
            context.runOnClient(client -> layout(client.gui.screen()));
            screenshot(context, "quality-tr-minimum-settings-interaction");
            context.clickScreenButton("gui.cancel");
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getInventory().clearContent();
                player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                player.getInventory().setItem(0, new ItemStack(Items.APPLE, 5));
                player.getFoodData().setFoodLevel(14);
                player.getFoodData().setSaturation(2.5f);
                player.inventoryMenu.broadcastChanges();
            });
            context.waitFor(client -> client.player.getMainHandItem().is(Items.APPLE)
                    && Math.abs(ClientMetrics.saturation() - 2.5f) < .01f, 100);
            context.runOnClient(client -> {
                var lines = Screen.getTooltipFromItem(client, client.player.getMainHandItem());
                check(lines.stream().anyMatch(line -> line.getString().equals("Yemek: +4 açlık, +2.4 doygunluk")),
                        "Turkish food tooltip values were not localized");
                check(lines.stream().anyMatch(line -> line.getString().equals("Doygunluk: 2.5 → 4.9")),
                        "Turkish saturation preview tooltip incorrect");
            });
            screenshot(context, "quality-tr-minimum-food-preview");
            context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            context.waitTicks(2);
            context.runOnClient(client -> {
                double scale = client.getWindow().getGuiScale();
                cursor[0] = ((client.gui.screen().width - 176) / 2.0 + 16) * scale;
                cursor[1] = ((client.gui.screen().height - 166) / 2.0 + 150) * scale;
            });
            context.getInput().setCursorPos(cursor[0], cursor[1]);
            screenshot(context, "quality-tr-minimum-food-tooltip");
            context.setScreen(() -> null);
            context.getInput().pressKey(InputConstants.KEY_F3);
            screenshot(context, "quality-tr-minimum-f3");
            context.getInput().pressKey(InputConstants.KEY_F3);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(Items.BOW));
                for (int slot = 1; slot < 36; slot++) player.getInventory().setItem(slot,
                        slot % 2 == 0 ? poison(slot) : new ItemStack(Items.ARROW, slot));
                player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SPECTRAL_ARROW));
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1000));
                player.inventoryMenu.broadcastChanges();
            });
            context.waitFor(client -> client.player.getMainHandItem().is(Items.BOW)
                    && client.player.getOffhandItem().is(Items.SPECTRAL_ARROW), 100);
            context.runOnClient(client -> {
                check(AmmoGroups.arrows(client.player, client.player.getMainHandItem()).size() == 36,
                        "Crowded HUD fixture does not contain all 36 ordered groups");
                PiggyClient.config.hudX = 0; PiggyClient.config.hudY = 0;
            });
            context.getInput().pressKey(InputConstants.KEY_F3);
            screenshot(context, "quality-tr-minimum-f3-36-arrow-groups-effects");
            context.runOnClient(client -> PerformanceProbe.sample("F3 lines + layout queries", () -> {
                SimpleDebug.lines(); SimpleDebug.counterTop(60); SimpleDebug.bounds(320); SimpleDebug.extraBounds(320);
            }));
            context.runOnClient(client -> {
                var debug = SimpleDebug.bounds(320);
                for (var panel : HudFeatures.bounds()) {
                    check(panel.inside(320, 240), "Crowded HUD extends outside minimum screen");
                    check(!panel.overlaps(debug), "Ammo and F3 overlap");
                }
                var occupied = new java.util.ArrayList<>(HudFeatures.bounds()); occupied.add(debug);
                var speed = HudLayout.avoid(new HudLayout.Rectangle(1, 1, 48, 15), occupied, 320, 240);
                check(speed.inside(320, 240) && occupied.stream().noneMatch(speed::overlaps), "Speed HUD collision was not resolved");
            });
            context.getInput().pressKey(InputConstants.KEY_F3);
            screenshot(context, "quality-tr-minimum-36-arrow-groups-food-effects");
            context.runOnClient(client -> {
                var panels = HudFeatures.bounds();
                for (int i = 0; i < panels.size(); i++) for (int j = i + 1; j < panels.size(); j++)
                    check(!panels.get(i).overlaps(panels.get(j)), "Food and ammo panels overlap");
            });
        } finally {
            context.getInput().releaseKey(InputConstants.KEY_F3);
            context.getInput().releaseKey(InputConstants.KEY_F1);
            context.setScreen(() -> null);
            context.runOnClient(client -> {
                if (client.gui.hud.isHidden()) client.gui.hud.toggle();
                client.options.reducedDebugInfo().set(false);
                client.debugEntries.setOverlayVisible(false);
                PiggyClient.config = originalMovement;
                QualityClient.save(new QualityConfig());
                PiggyClient.applySettings();
            });
            context.getInput().resizeWindow(originalDisplay[0], originalDisplay[1]);
            context.runOnClient(client -> {
                client.options.guiScale().set(originalDisplay[2]);
                client.resizeGui();
                client.gui.toastManager().clear();
            });
            if (!originalLanguage[0].equals("tr_tr")) language(context, originalLanguage[0]);
        }
    }
}
