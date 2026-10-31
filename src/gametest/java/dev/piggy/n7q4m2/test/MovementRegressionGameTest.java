package dev.piggy.n7q4m2.test;

import com.mojang.authlib.GameProfile;
import dev.piggy.n7q4m2.Piggy;
import dev.piggy.n7q4m2.client.PiggyClient;
import dev.piggy.n7q4m2.client.HopConfigScreen;
import dev.piggy.n7q4m2.config.MovementSettings;
import dev.piggy.n7q4m2.movement.HopAccess;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import dev.piggy.n7q4m2.network.HopPayloads;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import java.util.UUID;

/** Real packet-boundary and effective-damage regressions, excluded from release jars. */
public final class MovementRegressionGameTest implements FabricClientGameTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void move(ServerPlayer p, double distance) {
        p.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(p.getX() + distance, p.getY(), p.getZ(), true, false));
    }
    private static int teleportId(ServerPlayer p) {
        try {
            var field = ServerGamePacketListenerImpl.class.getDeclaredField("awaitingTeleport");
            field.setAccessible(true); return field.getInt(p.connection);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    @Override public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("piggy.preparePlaytest")) return;
        var saved = PiggyClient.config.copy();
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("difficulty peaceful");
            world.getServer().runCommand("gamerule player_movement_check true");
            context.waitFor(client -> client.player != null && PiggyClient.supported && client.gui.screen() == null, 200);
            context.waitTicks(30);
            world.getServer().runOnServer(server -> {
                var p = server.getPlayerList().getPlayers().getFirst();
                var a = (HopAccess)p;
                var owner = server.getSingleplayerProfile();
                // Exercise vanilla's non-owner checks, as on a multiplayer server.
                server.setSingleplayerProfile(new GameProfile(UUID.randomUUID(), "BoundaryOwner"));
                try {
                    check(p.connection.hasClientLoaded(), "Packet fixture not loaded");
                    a.piggy$envelope().reset();
                    double x = p.getX(); move(p, .1);
                    check(Math.abs(p.getX() - x - .1) < 1e-6, "Ordinary movement was rejected");
                    x = p.getX(); move(p, 4);
                    check(p.getX() == x, "Forged 80 b/s move bypassed configured speed/acceleration");
                    // ACK and retry inside the same server tick: corrections must not mint credit.
                    for (int attempt = 0; attempt < 10; attempt++) {
                        p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(teleportId(p), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()));
                        move(p, .2);
                        check(p.getX() == x, "Teleport ACK minted same-tick movement credit");
                    }
                    // Leave the last correction pending so normal client ACKs resolve it.
                } finally { server.setSingleplayerProfile(owner); }
            });
            context.waitTicks(8);
            world.getServer().runOnServer(server -> {
                var p = server.getPlayerList().getPlayers().getFirst(); var a = (HopAccess)p;
                var owner = server.getSingleplayerProfile();
                server.setSingleplayerProfile(new GameProfile(UUID.randomUUID(), "BoundaryOwner"));
                try {
                    // Server-authored knockback may exceed the selected bhop cap.
                    a.piggy$envelope().reset();
                    p.connection.send(new ClientboundSetEntityMotionPacket(p.getId(), new Vec3(4, 0, 0)));
                    double x = p.getX(); move(p, 4);
                    check(Math.abs(p.getX() - x - 4) < 1e-6, "Server impulse above cap was rejected");
                    check(Math.abs(a.piggy$state().impactSpeedBps() - 80) < 1e-6, "Impact speed was clipped to bhop cap");
                    // Six already-earned ticks arriving together; no per-packet grant.
                    a.piggy$envelope().reset();
                    a.piggy$envelope().advance(p.tickCount - 6, .25, 2, .08, true);
                    p.connection.send(new ClientboundSetEntityMotionPacket(p.getId(), new Vec3(2, 0, 0)));
                    a.piggy$envelope().forfeit();
                    x = p.getX();
                    for (int i = 0; i < 6; i++) move(p, 2);
                    check(Math.abs(p.getX() - x - 12) < 1e-6, "Six-tick lag burst was rejected");
                    move(p, .2);
                    check(Math.abs(p.getX() - x - 12) < 1e-6, "Burst packets manufactured extra tick credit");
                } finally { server.setSingleplayerProfile(owner); }
            });
            context.waitTicks(8);
            world.getServer().runOnServer(server -> {
                var p = server.getPlayerList().getPlayers().getFirst(); var a = (HopAccess)p;
                // Disabled/no-handshake movement still has a vanilla world-derived bound.
                a.piggy$enabled(false); a.piggy$envelope().reset();
                double x = p.getX(); move(p, 10);
                check(p.getX() == x, "Disabling bhop bypassed movement validation");
                a.piggy$enabled(true); a.piggy$settings(new MovementSettings(8, 10, 6, 1.5, 40, true, true));
                var owner = server.getSingleplayerProfile();
                server.setSingleplayerProfile(new GameProfile(UUID.randomUUID(), "BoundaryOwner"));
                try {
                    // Vanilla vertical rejection must also preserve the spent clock.
                    p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(teleportId(p), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()));
                    double y = p.getY();
                    p.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(p.getX(), y + 100, p.getZ(), false, false));
                    check(p.getY() == y, "Horizontal validation disabled vanilla vertical protection");
                    p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(teleportId(p), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()));
                    move(p, .2);
                    check(p.getX() == x, "Vanilla correction ACK minted movement credit");
                } finally { server.setSingleplayerProfile(owner); }
            });
            context.waitTicks(8);
            world.getServer().runOnServer(server -> {
                var p = server.getPlayerList().getPlayers().getFirst(); var a = (HopAccess)p;
                p.getInventory().clearContent(); p.removeAllEffects(); p.setAbsorptionAmount(0);
                p.getAbilities().invulnerable = false; p.setInvulnerableTime(0); p.setHealth(5);
                var totem = new ItemStack(Items.TOTEM_OF_UNDYING);
                p.getInventory().setSelectedSlot(0); p.getInventory().setItem(0, totem);
                a.piggy$state().setActive(true); a.piggy$state().impactSpeedBps(0);
                p.causeFallDamage(19, 1, p.damageSources().fall());
                check(p.isAlive() && totem.isEmpty() && p.getAbsorptionAmount() > 0, "Low-health fall did not consume real totem");
                check(!a.piggy$state().active(), "Totem healing hid effective fall damage");
                p.removeAllEffects(); p.setHealth(20);
                p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 0));
                p.setAbsorptionAmount(1); p.setInvulnerableTime(0);
                a.piggy$state().setActive(true);
                p.hurtServer(p.level(), p.damageSources().fall(), .5f);
                check(p.getHealth() == 20 && p.getAbsorptionAmount() == .5f && !a.piggy$state().active(),
                        "Absorption-only fall damage failed to end bhop");
                p.getAbilities().invulnerable = true;
                a.piggy$state().setActive(true); p.setInvulnerableTime(0);
                p.hurtServer(p.level(), p.damageSources().fall(), 1);
                check(a.piggy$state().active(), "Rejected fall damage ended bhop");
                p.getAbilities().invulnerable = false; p.removeAllEffects(); p.setAbsorptionAmount(0);
                a.piggy$state().reset(); p.setDeltaMovement(Vec3.ZERO);
                Piggy.policy().maxAirAcceleration = 40; Piggy.policy().maxSpeedCapBps = 200;
                ServerPlayNetworking.send(p, new HopPayloads.Policy(Piggy.policy()));
            });
            context.runOnClient(client -> { PiggyClient.config.bhopEnabled = false; PiggyClient.applySettings(); });
            context.waitTicks(6);
            world.getServer().runOnServer(server -> {
                var p = server.getPlayerList().getPlayers().getFirst();
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 64; z++) {
                    p.level().setBlock(new BlockPos(x, 80, z), Blocks.BLUE_ICE.defaultBlockState(), 3);
                    p.level().setBlock(new BlockPos(x, 81, z), Blocks.AIR.defaultBlockState(), 3);
                    p.level().setBlock(new BlockPos(x, 82, z), Blocks.AIR.defaultBlockState(), 3);
                    p.level().setBlock(new BlockPos(x, 83, z), Blocks.STONE.defaultBlockState(), 3);
                }
                p.teleportTo(0.5, 81, 0.5);
            });
            context.waitFor(client -> Math.abs(client.player.getY() - 81) < .01 && client.gui.screen() == null, 100);
            context.waitTicks(8);
            int[] beforeTeleport = {world.getServer().computeOnServer(server -> teleportId(server.getPlayerList().getPlayers().getFirst()))};
            context.runOnClient(client -> {
                client.player.setYRot(0); client.player.setXRot(0);
                client.player.yRotO = 0; client.player.xRotO = 0;
            });
            context.getInput().holdKey(options -> options.keyUp);
            context.getInput().holdKey(options -> options.keySprint);
            context.getInput().holdKey(options -> options.keyJump);
            double[] peak = {0};
            for (int i = 0; i < 90; i++) {
                context.waitTick();
                context.runOnClient(client -> peak[0] = Math.max(peak[0], client.player.getDeltaMovement().horizontalDistance()));
            }
            context.getInput().releaseKey(options -> options.keyUp);
            context.getInput().releaseKey(options -> options.keySprint);
            context.getInput().releaseKey(options -> options.keyJump);
            check(peak[0] > .48, "Vanilla blue-ice fixture did not exceed the old bound: " + peak[0]);
            world.getServer().runOnServer(server -> {
                var p = server.getPlayerList().getPlayers().getFirst();
                check(teleportId(p) == beforeTeleport[0], "Legitimate vanilla ice sprint-jumps were corrected");
            });
            context.waitFor(client -> PiggyClient.policy.maxAirAcceleration == 40, 100);
            context.setScreen(() -> new HopConfigScreen(null));
            context.runOnClient(client -> {
                var boxes = client.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
                boxes.get(0).setValue("30"); boxes.get(2).setValue("120");
            });
            context.clickScreenButton("piggy.save");
            context.waitTicks(6);
            context.runOnClient(client -> check(PiggyClient.config.airAcceleration == 30 && PiggyClient.config.maxSpeedBps == 120,
                    "Save silently clipped server-valid preferences"));
            world.getServer().runOnServer(server -> {
                var settings = ((HopAccess)server.getPlayerList().getPlayers().getFirst()).piggy$settings();
                check(settings.airAcceleration() == 30 && settings.maxSpeedBps() == 120, "Saved preferences failed server sync");
            });
        } finally {
            context.getInput().releaseKey(options -> options.keyUp);
            context.getInput().releaseKey(options -> options.keySprint);
            context.getInput().releaseKey(options -> options.keyJump);
            context.setScreen(() -> null);
            context.runOnClient(client -> PiggyClient.save(saved));
        }
    }
}
