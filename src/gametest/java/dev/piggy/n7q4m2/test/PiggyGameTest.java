package dev.piggy.n7q4m2.test;

import dev.piggy.n7q4m2.client.PiggyClient;
import dev.piggy.n7q4m2.client.PiggyModMenu;
import dev.piggy.n7q4m2.client.HopConfigScreen;
import dev.piggy.n7q4m2.movement.HopAccess;
import dev.piggy.n7q4m2.movement.HopPhysics;
import com.terraformersmc.modmenu.gui.ModsScreen;
import com.terraformersmc.modmenu.gui.widget.ModListWidget;
import com.terraformersmc.modmenu.gui.widget.entries.ModListEntry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;

/** Runs a real client and integrated server. This source set is excluded from the released jar. */
public final class PiggyGameTest implements FabricClientGameTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @Override public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("piggy.preparePlaytest")) {
            try (var world = context.worldBuilder().create()) {
                world.getServer().runCommand("gamemode survival @a");
                world.getServer().runCommand("difficulty peaceful");
                world.getServer().runCommand("time set day");
                world.getServer().runCommand("setworldspawn 0 -60 0");
                world.getServer().runCommand("spawnpoint @a 0 -60 0");
                world.getServer().runCommand("tp @a 0 -60 0 0 5");
                context.waitTicks(20);
            }
            return;
        }
        var metadata = FabricLoader.getInstance().getModContainer("piggy").orElseThrow().getMetadata();
        check(metadata.getName().equals("Piggy") && metadata.getDescription().equals("Adds some new features i like"),
                "Mod Menu metadata differs from requested branding");
        check(metadata.getIconPath(32).orElseThrow().equals("assets/piggy/icon.png"), "Mod Menu icon path missing");
        context.setScreen(() -> new ModsScreen(null));
        context.runOnClient(client -> client.gui.screen().children().stream().filter(EditBox.class::isInstance)
                .map(EditBox.class::cast).findFirst().orElseThrow().setValue("Piggy"));
        context.waitTicks(3);
        context.runOnClient(client -> {
            var screen = (ModsScreen)client.gui.screen();
            var list = screen.children().stream().filter(ModListWidget.class::isInstance).map(ModListWidget.class::cast).findFirst().orElseThrow();
            var entry = list.children().stream().filter(ModListEntry.class::isInstance).map(ModListEntry.class::cast)
                    .filter(e -> e.getMod().getId().equals("piggy")).findFirst().orElseThrow();
            screen.updateSelectedEntry(entry);
            check(screen.getSelectedEntry().getMod().getName().equals("Piggy"), "Mod Menu did not select new mod branding");
        });
        context.takeScreenshot("piggy-mod-menu-icon");
        context.setScreen(() -> new PiggyModMenu().getModConfigScreenFactory().create(null));
        context.clickScreenButton("piggy.menu.bunny");
        context.waitTicks(2);
        context.runOnClient(client -> {
            check(client.gui.screen() instanceof HopConfigScreen, "Mod Menu did not open Piggy settings");
            var widgets = client.gui.screen().children().stream().filter(AbstractWidget.class::isInstance)
                    .map(AbstractWidget.class::cast).toList();
            for (int i = 0; i < widgets.size(); i++) {
                var a = widgets.get(i);
                check(a.getY() >= 0 && a.getY() + a.getHeight() <= client.gui.screen().height, "Widget outside screen");
                for (int j = i + 1; j < widgets.size(); j++) {
                    var b = widgets.get(j);
                    check(!(a.getX() < b.getX() + b.getWidth() && a.getX() + a.getWidth() > b.getX()
                            && a.getY() < b.getY() + b.getHeight() && a.getY() + a.getHeight() > b.getY()), "Settings widgets overlap");
                }
            }
        });
        context.takeScreenshot("piggy-settings");
        context.runOnClient(client -> {
            var inputs = client.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
            check(inputs.size() == 3, "Movement sliders do not all have numeric inputs");
            var save = client.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(b -> b.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable("piggy.save").getString())).findFirst().orElseThrow();
            inputs.getFirst().setValue("5.5");
            check(save.active, "Typed decimal was rejected");
            inputs.getFirst().setValue("5,5");
            check(save.active, "Comma decimal was rejected");
            inputs.getFirst().setValue("999999");
            check(!save.active, "Server-limit violation can be saved");
            inputs.getFirst().setValue("NaN");
            check(!save.active, "Non-finite number can be saved");
            inputs.getFirst().setValue("5.5");
        });
        context.takeScreenshot("piggy-numeric-input");
        context.clickScreenButton("piggy.save");
        context.runOnClient(client -> check(PiggyClient.config.airAcceleration == 5.5, "Typed 5.5 was not saved exactly"));
        context.setScreen(() -> new HopConfigScreen(null));
        context.runOnClient(client -> check(client.gui.screen().children().stream().filter(EditBox.class::isInstance)
                .map(EditBox.class::cast).findFirst().orElseThrow().getValue().equals("5.5"), "Saved decimal was not restored"));
        context.setScreen(() -> new HopConfigScreen(null, HopConfigScreen.Page.SPEED_HUD));
        context.waitTicks(2);
        context.takeScreenshot("piggy-hud-settings");
        context.clickScreenButton("gui.cancel");

        context.runOnClient(client -> { PiggyClient.config.bhopEnabled = true; PiggyClient.applySettings(); });
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode survival @a");
            context.waitFor(client -> client.player != null && PiggyClient.supported && ((HopAccess) client.player).piggy$enabled());
            context.waitTicks(30);
            context.getInput().holdKey(options -> options.keyUp);
            context.waitTicks(20);
            context.runOnClient(client -> {
                check(!((HopAccess) client.player).piggy$state().active(), "Walking activated bhop without jumping");
                check(client.player.getDeltaMovement().horizontalDistance() * 20 < 6, "Normal walking received bhop acceleration");
            });
            context.getInput().holdKey(options -> options.keyJump);
            context.waitTicks(35);
            context.runOnClient(client -> check(((HopAccess) client.player).piggy$enabled(),
                    "Menu preference did not keep movement enabled"));
            context.getInput().releaseKey(options -> options.keyJump);
            context.waitFor(client -> client.player.onGround(), 40);
            context.getInput().holdKey(options -> options.keySprint);
            context.waitTicks(2);
            context.getInput().holdKey(options -> options.keyJump);
            context.waitFor(client -> ((HopAccess) client.player).piggy$state().active(), 100);
            context.runOnClient(client -> {
                check(client.player.getDeltaMovement().horizontalDistance() > .1, "Bhop has no horizontal movement");
                float fov = client.player.getFieldOfViewModifier(true, 1);
                check(Math.abs(fov - 1.15f) < .00001, "Active bhop FOV is not the vanilla sprint value");
                client.player.setSprinting(false);
                check(Math.abs(client.player.getFieldOfViewModifier(true, 1) - fov) < .00001, "Bhop FOV changes when sprint flag changes");
                check(client.player.getFieldOfViewModifier(true, 0) == 1, "Bhop ignores FOV-effects preference");
                client.player.setSprinting(true);
            });
            context.waitTicks(3);
            world.getServer().runOnServer(server -> check(((HopAccess) server.getPlayerList().getPlayers().getFirst()).piggy$state().active(), "Server did not validate bhop activation"));
            context.takeScreenshot("piggy-active");
            context.getInput().releaseKey(options -> options.keyJump);
            context.waitFor(client -> client.player.onGround(), 30);
            context.waitTicks(12);
            context.runOnClient(client -> {
                check(client.player.getDeltaMovement().horizontalDistance() * 20 < 6,
                        "Ground running kept bhop momentum instead of vanilla friction");
                check(!((HopAccess) client.player).piggy$state().active(), "Late jump kept the chain while walking");
            });
            context.getInput().holdKey(options -> options.keyShift);
            context.waitTicks(2);
            context.runOnClient(client -> check(!((HopAccess) client.player).piggy$state().active(), "Shift did not end bhop"));
            context.getInput().releaseKey(options -> options.keyShift);
            context.getInput().releaseKey(options -> options.keyJump);
            context.getInput().releaseKey(options -> options.keyUp);
            context.getInput().releaseKey(options -> options.keySprint);

            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                HopAccess access = (HopAccess) player;
                player.setHealth(20); player.setInvulnerableTime(0);
                access.piggy$state().setActive(true);
                player.hurtServer(player.level(), player.damageSources().generic(), 1);
                check(access.piggy$state().active(), "Non-fall attack ended bhop");

                player.setHealth(20); player.setInvulnerableTime(0);
                access.piggy$state().impactSpeedBps(HopPhysics.FALL_REFERENCE_BPS * 4.01);
                player.causeFallDamage(4, 1, player.damageSources().fall());
                check(player.getHealth() == 20 && access.piggy$state().active(), "Sub-half-point fall did not preserve bhop");

                player.setInvulnerableTime(0);
                access.piggy$state().impactSpeedBps(HopPhysics.FALL_REFERENCE_BPS * 4);
                player.causeFallDamage(4, 1, player.damageSources().fall());
                check(Math.abs(player.getHealth() - 19.5f) < .001, "Exactly 0.5 fall damage was rounded incorrectly");
                check(!access.piggy$state().active(), "Fall damage did not end bhop");

                player.setHealth(20); player.setInvulnerableTime(0);
                access.piggy$state().setActive(true);
                access.piggy$state().impactSpeedBps(HopPhysics.FALL_REFERENCE_BPS * 4);
                player.causeFallDamage(19, 1, player.damageSources().fall());
                check(player.getHealth() == 12, "16 damage at 4x speed did not become 8");
            });

            context.waitTicks(3);
            // Check actual travel hooks without network latency between individual measurements.
            context.runOnClient(client -> {
                var player = client.player;
                HopAccess access = (HopAccess) player;
                access.piggy$state().setActive(true);
                player.setPos(player.getX(), player.getY() + 4, player.getZ());
                player.setOnGround(false); player.horizontalCollision = false;
                player.setDeltaMovement(.5, 0, 0);
                player.input.keyPresses = new net.minecraft.world.entity.player.Input(true, false, false, false, false, false, false);
                player.travel(Vec3.ZERO);
                check(Math.abs(player.getDeltaMovement().x - .5) < .00001, "Holding a movement key lost air speed");
                player.input.keyPresses = net.minecraft.world.entity.player.Input.EMPTY;
                player.travel(Vec3.ZERO);
                check(player.getDeltaMovement().x < .5, "Releasing movement keys did not apply air friction");
                check(access.piggy$state().active(), "Releasing keys ended bhop in the air");

                player.setOnGround(false); player.horizontalCollision = false;
                player.setYRot(0); player.setDeltaMovement(0, 0, .5);
                player.input.keyPresses = new net.minecraft.world.entity.player.Input(true, false, false, false, false, false, false);
                player.travel(new Vec3(0, 0, 1));
                player.setYRot(90);
                player.travel(new Vec3(0, 0, 1));
                check(player.getDeltaMovement().x < -.49, "A sharp mouse turn did not redirect bhop momentum");
                check(player.getDeltaMovement().horizontalDistance() >= .499, "Sharp air turn reduced speed");

                // Use a temporary local fixture and restore it before the next network tick.
                Vec3 oldPosition = player.position();
                BlockPos base = BlockPos.containing(oldPosition).above(8);
                var wall = base.east();
                var wallState = client.level.getBlockState(wall);
                var wallTopState = client.level.getBlockState(wall.above());
                client.level.setBlock(wall, Blocks.STONE.defaultBlockState(), 0);
                client.level.setBlock(wall.above(), Blocks.STONE.defaultBlockState(), 0);
                player.setPos(base.getX() + .5, base.getY(), base.getZ() + .5);
                player.setOnGround(false); player.setDeltaMovement(.5, 0, 0);
                player.move(MoverType.SELF, player.getDeltaMovement());
                check(player.horizontalCollision, "Wall fixture did not collide");
                check(Math.abs(access.piggy$state().retainedSpeed() - .5) < .00001, "Wall erased retained momentum");
                player.setYRot(0);
                player.input.keyPresses = new net.minecraft.world.entity.player.Input(true, false, false, false, false, false, false);
                player.travel(new Vec3(0, 0, 1));
                check(player.getDeltaMovement().horizontalDistance() >= .499, "Turning away from wall did not restore speed");
                client.level.setBlock(wall, wallState, 0);
                client.level.setBlock(wall.above(), wallTopState, 0);

                var ceiling = base.above(2);
                var ceilingState = client.level.getBlockState(ceiling);
                client.level.setBlock(ceiling, Blocks.STONE.defaultBlockState(), 0);
                player.setPos(base.getX() + .5, base.getY(), base.getZ() + .5);
                player.setOnGround(false); player.setDeltaMovement(.1, .42, 0);
                player.move(MoverType.SELF, player.getDeltaMovement());
                check(player.verticalCollision, "Ceiling fixture did not collide");
                check(Math.abs(player.getDeltaMovement().x - .1) < .00001, "Ceiling erased horizontal momentum");
                client.level.setBlock(ceiling, ceilingState, 0);

                var floor = base.below();
                var floorState = client.level.getBlockState(floor);
                client.level.setBlock(floor, Blocks.SOUL_SAND.defaultBlockState(), 0);
                player.setPos(base.getX() + .5, base.getY(), base.getZ() + .5);
                player.setOnGround(true); player.setDeltaMovement(.1, -.08, 0);
                player.move(MoverType.SELF, player.getDeltaMovement());
                check(player.getDeltaMovement().x < .1, "Bhop bypassed soul sand slowdown");

                client.level.setBlock(floor, Blocks.SLIME_BLOCK.defaultBlockState(), 0);
                player.setPos(base.getX() + .5, base.getY() + .2, base.getZ() + .5);
                player.setOnGround(false); player.setDeltaMovement(.1, -.6, 0);
                player.move(MoverType.SELF, player.getDeltaMovement());
                check(player.getDeltaMovement().y > 0, "Bhop removed slime bounce");
                client.level.setBlock(floor, floorState, 0);
                player.setPos(oldPosition); player.setDeltaMovement(Vec3.ZERO);
                access.piggy$state().reset();
            });

            world.getServer().runOnServer(server -> {
                var nether = server.getLevel(Level.NETHER);
                nether.getChunk(0, 0);
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                    nether.setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState(), 3);
                    for (int y = 81; y <= 85; y++) nether.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            });
            // Reset the synthetic collision/travel fixture through a real server teleport before the next input sequence.
            world.getServer().runCommand("tp @a 0.5 -60 0.5 0 0");
            context.waitFor(client -> client.player.onGround() && client.player.position().distanceToSqr(new Vec3(.5, -60, .5)) < .05, 80);
            world.getServer().runOnServer(server -> dev.piggy.n7q4m2.movement.HopController.stop(
                    server.getPlayerList().getPlayers().getFirst()));
            context.waitTicks(4);
            context.getInput().holdKey(options -> options.keyUp);
            context.getInput().holdKey(options -> options.keySprint);
            context.waitTicks(2);
            context.getInput().holdKey(options -> options.keyJump);
            context.waitFor(client -> ((HopAccess) client.player).piggy$state().active() && !client.player.onGround(), 100);
            context.waitTicks(2);
            context.runOnClient(client -> client.player.setDeltaMovement(0, .42, .5));
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var nether = server.getLevel(Level.NETHER);
                check(((HopAccess) player).piggy$state().active(), "Dimension fixture must start from a real bhop chain");
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player.getId(), new Vec3(0, .42, .5)));
                double authorizedSpeed = ((HopAccess) player).piggy$envelope().speed();
                // A lag batch can report a much larger displacement than physical
                // per-tick velocity. Dimension transfer must not authorize that speed.
                player.setKnownMovement(new Vec3(0, .42, 20));
                player.setDeltaMovement(0, .42, .5);
                player.teleport(new TeleportTransition(nether, new Vec3(4.5, 83, 4.5), Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
                check(((HopAccess) player).piggy$state().active(), "Dimension transfer ended server bhop");
                check(player.getDeltaMovement().horizontalDistance() >= .49, "Dimension transfer erased server speed");
                check(player.getDeltaMovement().horizontalDistance() <= authorizedSpeed + 1e-6,
                        "Dimension transfer promoted packet displacement into trusted impulse");
            });
            context.waitFor(client -> client.player != null && client.player.level().dimension() == Level.NETHER && client.gui.screen() == null, 200);
            context.runOnClient(client -> {
                check(((HopAccess) client.player).piggy$state().active(), "Dimension transfer ended client bhop");
                check(client.player.getDeltaMovement().horizontalDistance() > .3, "Dimension transfer erased client momentum");
            });
            context.getInput().releaseKey(options -> options.keyUp);
            context.getInput().releaseKey(options -> options.keyJump);
            context.getInput().releaseKey(options -> options.keySprint);
            context.waitTicks(3);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                ((HopAccess) player).piggy$state().setActive(true);
                player.teleportTo(player.getX() + 2, player.getY(), player.getZ());
                check(!((HopAccess) player).piggy$state().active(), "Same-dimension teleport did not end bhop");
                check(player.getDeltaMovement().equals(Vec3.ZERO), "Same-dimension teleport did not reset speed");
            });
            context.waitTicks(3);
            context.runOnClient(client -> check(!((HopAccess) client.player).piggy$state().active(), "Teleport did not reset client bhop"));
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var vehicle = net.minecraft.world.entity.EntityTypes.OAK_BOAT.create(player.level(), EntitySpawnReason.TRIGGERED);
                vehicle.setPos(player.position());
                player.level().addFreshEntity(vehicle);
                ((HopAccess) player).piggy$state().setActive(true);
                check(player.startRiding(vehicle, true, false), "Boat mounting fixture failed");
                check(!((HopAccess) player).piggy$state().active(), "Mounting a boat did not end bhop");
                player.stopRiding();
                player.setInvulnerableTime(0);
                ((HopAccess) player).piggy$state().setActive(true);
                player.setDeltaMovement(.5, 0, .5);
                player.hurtServer(player.level(), player.damageSources().genericKill(), 1000);
                check(!((HopAccess) player).piggy$state().active(), "Death did not end bhop");
                check(player.getDeltaMovement().equals(Vec3.ZERO), "Death did not reset speed");
            });
        }
    }
}
