package dev.piggy.n7q4m2.test;

import dev.piggy.n7q4m2.client.quality.QualityClient;
import dev.piggy.n7q4m2.client.quality.QualityConfig;
import dev.piggy.n7q4m2.client.quality.placement.PlacementAssist;
import dev.piggy.n7q4m2.quality.QualityOfLife;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Real integrated-server interactions, plus deterministic client aiming fixtures. */
public final class QualityPlacementGameTest implements FabricClientGameTest {
    private static final BlockPos LEFT_DOOR = new BlockPos(2, 81, 0);
    private static final BlockPos RIGHT_DOOR = LEFT_DOOR.east();
    private static final BlockPos GHOST = new BlockPos(0, 80, 3);
    private static final Vec3 BRIDGE_POSITION = new Vec3(0.5, 81, 1.5);
    private static final Vec3 GHOST_AIM = new Vec3(0.5, 80.8, 3.4);

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @Override public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("piggy.preparePlaytest")) return;
        QualityConfig previous = context.computeOnClient(client -> QualityClient.config.copy());
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("difficulty peaceful");
            context.waitFor(client -> client.player != null && client.getConnection() != null);
            context.runOnClient(client -> {
                QualityClient.config.pairedDoors = true;
                QualityClient.config.placementAssist = true;
                QualityClient.config.autoRestock = false;
                QualityClient.sync();
            });
            awaitDoorPreference(context, world.getServer(), true);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = player.level();
                player.getInventory().clearContent();
                player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                player.setPos(2.5, 81, 2.5);
                for (int x = 1; x <= 4; x++) for (int z = -1; z <= 3; z++)
                    level.setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState(), 3);
                runDoorChecks(player);
            });
            context.runOnClient(client -> { QualityClient.config.pairedDoors = false; QualityClient.sync(); });
            awaitDoorPreference(context, world.getServer(), false);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setPos(2.5, 81, 2.5);
                fixture(player.level(), oak(DoorHingeSide.LEFT), oak(DoorHingeSide.RIGHT));
                toggle(player, LEFT_DOOR);
                assertDoor(player.level(), LEFT_DOOR, true, "Disabled pairing still lets vanilla door open");
                assertDoor(player.level(), RIGHT_DOOR, false, "Disabled pairing changed secondary door");
            });
            context.runOnClient(client -> { QualityClient.config.pairedDoors = true; QualityClient.sync(); });
            awaitDoorPreference(context, world.getServer(), true);

            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = player.level();
                // Clear a raised test area so distant flat-world terrain never acts as a bridge support.
                for (int x = -5; x <= 5; x++) for (int z = -5; z <= 9; z++) for (int y = 79; y <= 85; y++)
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                for (int z = -2; z <= 2; z++) level.setBlock(new BlockPos(0, 80, z), Blocks.STONE.defaultBlockState(), 3);
                player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COBBLESTONE, 16));
                player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                player.inventoryMenu.broadcastChanges();
            });
            world.getServer().runCommand("tp @a 0.5 81 1.5 0 44");
            context.waitFor(client -> client.gui.screen() == null && client.getCameraEntity() == client.player
                    && client.player.position().distanceToSqr(BRIDGE_POSITION) < 0.05
                    && client.player.getMainHandItem().is(Items.COBBLESTONE)
                    && client.level.getBlockState(new BlockPos(0, 80, 2)).is(Blocks.STONE));
            context.runOnClient(client -> {
                aim(client, BRIDGE_POSITION, GHOST_AIM);
                PerformanceProbe.sample("bridge target search", () -> PlacementAssist.findTarget(client, 1));
                PlacementAssist.Target target = PlacementAssist.findTarget(client, 1);
                check(target != null && target.position().equals(GHOST), "Bridge edge did not produce the expected phantom cell: target="
                        + target + ", screen=" + client.gui.screen() + ", support=" + client.level.getBlockState(new BlockPos(0, 80, 2)));
                check(target.anchorHit().getBlockPos().relative(target.anchorHit().getDirection()).equals(GHOST), "Phantom uses an invalid support face");
                check(client.player.getEyePosition().distanceToSqr(target.anchorHit().getLocation())
                        <= Math.pow(client.player.blockInteractionRange(), 2), "Phantom anchor is outside vanilla interaction range");
                check(target.state().is(Blocks.COBBLESTONE) && !target.shape().isEmpty(), "Phantom geometry does not match held block");
                runPlacementRejectionChecks(client);
                aim(client, BRIDGE_POSITION, GHOST_AIM);
                PlacementAssist.updatePreview(client, 1);
            });
            world.getConnection().waitForChunksRender();
            context.waitFor(client -> PlacementAssist.preview() != null && PlacementAssist.preview().position().equals(GHOST), 40);
            context.takeScreenshot("piggy-placement-phantom");

            // Exercise the actual startUseItem mixin through a regular right-click binding.
            context.getInput().pressKey(options -> options.keyUse);
            context.waitFor(client -> client.level.getBlockState(GHOST).is(Blocks.COBBLESTONE), 40);
            context.waitTicks(3);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                check(player.level().getBlockState(GHOST).is(Blocks.COBBLESTONE), "Phantom placement was not accepted by server");
                check(player.getMainHandItem().is(Items.COBBLESTONE) && player.getMainHandItem().getCount() == 15,
                        "Survival phantom placement did not consume exactly one block");
            });
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                for (int z = 3; z <= 6; z++) player.level().setBlock(new BlockPos(0, 80, z), Blocks.AIR.defaultBlockState(), 3);
                player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COBBLESTONE, 16));
                player.inventoryMenu.broadcastChanges();
            });
            context.waitFor(client -> client.level.getBlockState(GHOST).isAir() && client.player.getMainHandItem().getCount() == 16, 60);
            context.runOnClient(client -> aim(client, BRIDGE_POSITION, GHOST_AIM));
            context.getInput().holdKey(options -> options.keyUse);
            context.waitFor(client -> client.level.getBlockState(GHOST).is(Blocks.COBBLESTONE) && PlacementAssist.bridging(), 40);
            context.runOnClient(client -> {
                aim(client, BRIDGE_POSITION, new Vec3(1.8, 80.8, 2.5));
                check(PlacementAssist.bridging(), "Bridge latch ended before button release");
                check(PlacementAssist.findTarget(client, 1) == null, "Held bridge changed direction");
            });
            // Drain the legitimate initial placement before taking the authoritative item baseline.
            context.waitTicks(2);
            int remaining = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().getFirst().getMainHandItem().getCount());
            context.waitTicks(6);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                check(player.getMainHandItem().getCount() == remaining && player.level().getBlockState(new BlockPos(1, 80, 2)).isAir(),
                        "Held bridge fell through to normal placement after looking away: before=" + remaining
                                + ", after=" + player.getMainHandItem().getCount() + ", east=" + player.level().getBlockState(new BlockPos(1, 80, 2)));
            });
            context.getInput().releaseKey(options -> options.keyUse);
            context.waitTick();
            context.runOnClient(client -> {
                check(!PlacementAssist.bridging(), "Releasing right-click did not end bridge latch");
                aim(client, BRIDGE_POSITION, new Vec3(1.8, 80.8, 2.5));
                check(PlacementAssist.findTarget(client, 1) != null, "Released bridge did not allow a new direction");
            });
            // Ordinary placement above bridge height: enforce the two-tick interval.
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.level().setBlock(new BlockPos(0, 82, 4), Blocks.STONE.defaultBlockState(), 3);
                for (int z = 1; z <= 3; z++) player.level().setBlock(new BlockPos(0, 82, z), Blocks.AIR.defaultBlockState(), 3);
                player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COBBLESTONE, 16));
                player.inventoryMenu.broadcastChanges();
            });
            context.waitFor(client -> client.level.getBlockState(new BlockPos(0, 82, 4)).is(Blocks.STONE), 60);
            context.runOnClient(client -> {
                aim(client, BRIDGE_POSITION, new Vec3(.5, 82.5, 4));
                check(PlacementAssist.findTarget(client, 1) == null, "Normal fast-placement fixture generated ghost");
            });
            context.waitTicks(3);
            context.getInput().holdKey(options -> options.keyUse);
            context.waitTicks(2);
            world.getServer().runOnServer(server -> {
                var level = server.getPlayerList().getPlayers().getFirst().level();
                check(level.getBlockState(new BlockPos(0, 82, 3)).is(Blocks.COBBLESTONE)
                        && level.getBlockState(new BlockPos(0, 82, 2)).isAir(), "Block placement interval is faster than two ticks");
            });
            context.waitTicks(2);
            context.getInput().releaseKey(options -> options.keyUse);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                check(player.level().getBlockState(new BlockPos(0, 82, 3)).is(Blocks.COBBLESTONE)
                        && player.level().getBlockState(new BlockPos(0, 82, 2)).is(Blocks.COBBLESTONE),
                        "Holding ordinary right-click did not place two blocks within four ticks");
            });
            runJumpBridge(context, world.getServer());
        } finally {
            context.getInput().releaseKey(options -> options.keyUse);
            context.getInput().releaseKey(options -> options.keyJump);
            context.getInput().releaseKey(options -> options.keyUp);
            context.getInput().releaseKey(options -> options.keySprint);
            context.runOnClient(client -> { QualityClient.config = previous; QualityClient.sync(); });
        }
    }

    private static void runJumpBridge(ClientGameTestContext context, TestServerContext server) {
        server.runOnServer(s -> {
            var player = s.getPlayerList().getPlayers().getFirst();
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 12; z++) for (int y = 80; y <= 85; y++)
                player.level().setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
            for (int z = -2; z <= 2; z++) player.level().setBlock(new BlockPos(0, 80, z), Blocks.STONE.defaultBlockState(), 3);
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COBBLESTONE, 64));
            player.setDeltaMovement(Vec3.ZERO);
            player.inventoryMenu.broadcastChanges();
        });
        server.runCommand("tp @a 0.5 81 1.5 0 44");
        context.waitFor(client -> client.player.position().distanceToSqr(BRIDGE_POSITION) < .05
                && client.player.getMainHandItem().getCount() == 64 && client.level.getBlockState(GHOST).isAir(), 100);
        context.runOnClient(client -> aim(client, BRIDGE_POSITION, GHOST_AIM));
        context.getInput().holdKey(options -> options.keyJump);
        context.getInput().holdKey(options -> options.keyUp);
        context.getInput().holdKey(options -> options.keySprint);
        context.waitFor(client -> client.player.getY() > 81.2, 20);
        context.runOnClient(client -> {
            lookAt(client, GHOST_AIM);
            var target = PlacementAssist.findTarget(client, 1);
            check(target != null && target.position().equals(GHOST), "Bridge cannot start during a running jump");
        });
        context.getInput().holdKey(options -> options.keyUse);
        int previousCount = 64, airPlacements = 0;
        for (int tick = 0; tick < 18; tick++) {
            context.runOnClient(client -> {
                int next = Math.max(3, (int)Math.floor(client.player.getZ()) + 1);
                while (next < 12 && !client.level.getBlockState(new BlockPos(0, 80, next)).isAir()) next++;
                lookAt(client, new Vec3(.5, 80.8, next + .4));
                if (PlacementAssist.bridging() && PlacementAssist.preview() != null)
                    check(PlacementAssist.preview().position().getY() == 80, "Running jump raises the locked bridge deck");
            });
            context.waitTick();
            int[] sample = server.computeOnServer(s -> {
                var player = s.getPlayerList().getPlayers().getFirst();
                return new int[]{player.getMainHandItem().getCount(), player.getY() > 81.1 ? 1 : 0};
            });
            if (sample[1] == 1 && sample[0] < previousCount) airPlacements += previousCount - sample[0];
            previousCount = sample[0];
        }
        context.getInput().releaseKey(options -> options.keyUse);
        context.getInput().releaseKey(options -> options.keyJump);
        context.getInput().releaseKey(options -> options.keyUp);
        context.getInput().releaseKey(options -> options.keySprint);
        check(airPlacements >= 2, "Running jump did not place at least two server-accepted bridge blocks in the air");
        server.runOnServer(s -> {
            var player = s.getPlayerList().getPlayers().getFirst();
            check(player.getZ() > 3.5, "Bridge fixture did not run forward across the original edge");
            check(player.level().getBlockState(GHOST).is(Blocks.COBBLESTONE), "Running jump did not fill the first bridge gap");
            for (int z = 3; z < 10; z++) check(player.level().getBlockState(new BlockPos(0, 81, z)).isAir(),
                    "Running jump incorrectly placed a raised bridge block");
        });
        context.takeScreenshot("piggy-running-jump-bridge");
    }

    private static void awaitDoorPreference(ClientGameTestContext context, TestServerContext server, boolean value) {
        for (int tick = 0; tick < 60; tick++) {
            if (server.computeOnServer(s -> !s.getPlayerList().getPlayers().isEmpty()
                    && QualityOfLife.settings(s.getPlayerList().getPlayers().getFirst()).pairedDoors() == value)) return;
            context.waitTick();
        }
        throw new AssertionError("Server did not receive paired-door preference " + value);
    }

    private static BlockState oak(DoorHingeSide hinge) {
        return Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HINGE, hinge).setValue(DoorBlock.OPEN, false).setValue(DoorBlock.POWERED, false);
    }

    private static void fixture(Level level, BlockState left, BlockState right) {
        door(level, LEFT_DOOR, left);
        door(level, RIGHT_DOOR, right);
    }

    private static void door(Level level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 2);
        level.setBlock(pos.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
    }

    private static void toggle(ServerPlayer player, BlockPos clicked) {
        var hit = new BlockHitResult(Vec3.atCenterOf(clicked), Direction.SOUTH, clicked, false);
        check(player.gameMode.useItemOn(player, player.level(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit).consumesAction(),
                "Vanilla door interaction did not succeed");
    }

    private static void assertDoor(Level level, BlockPos pos, boolean open, String message) {
        check(level.getBlockState(pos).getValue(DoorBlock.OPEN) == open
                && level.getBlockState(pos.above()).getValue(DoorBlock.OPEN) == open, message);
    }

    private static void runDoorChecks(ServerPlayer player) {
        Level level = player.level();
        fixture(level, oak(DoorHingeSide.LEFT), oak(DoorHingeSide.RIGHT));
        toggle(player, LEFT_DOOR);
        assertDoor(level, LEFT_DOOR, true, "Clicked door did not open both halves");
        assertDoor(level, RIGHT_DOOR, true, "Matching door did not follow lower-half opening");
        toggle(player, RIGHT_DOOR.above());
        assertDoor(level, LEFT_DOOR, false, "Matching door did not follow upper-half closing");
        assertDoor(level, RIGHT_DOOR, false, "Upper-half click did not close both halves");
        toggle(player, LEFT_DOOR.above());
        assertDoor(level, RIGHT_DOOR, true, "Upper-half opening did not synchronize partner");
        toggle(player, RIGHT_DOOR);
        assertDoor(level, LEFT_DOOR, false, "Lower-half closing did not synchronize partner");

        fixture(level, oak(DoorHingeSide.LEFT), oak(DoorHingeSide.LEFT));
        toggle(player, LEFT_DOOR);
        assertDoor(level, RIGHT_DOOR, false, "Same-hinge doors were incorrectly paired");
        fixture(level, oak(DoorHingeSide.LEFT), oak(DoorHingeSide.RIGHT).setValue(DoorBlock.FACING, Direction.SOUTH));
        toggle(player, LEFT_DOOR);
        assertDoor(level, RIGHT_DOOR, false, "Opposite-facing doors were incorrectly paired");
        fixture(level, oak(DoorHingeSide.LEFT), Blocks.BIRCH_DOOR.defaultBlockState().setValue(DoorBlock.HINGE, DoorHingeSide.RIGHT));
        toggle(player, LEFT_DOOR);
        assertDoor(level, RIGHT_DOOR, false, "Different door materials were incorrectly paired");
        fixture(level, oak(DoorHingeSide.LEFT), Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HINGE, DoorHingeSide.RIGHT));
        toggle(player, LEFT_DOOR);
        assertDoor(level, RIGHT_DOOR, false, "Paired helper opened an iron door");
        fixture(level, Blocks.IRON_DOOR.defaultBlockState(), Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HINGE, DoorHingeSide.RIGHT));
        var ironHit = new BlockHitResult(Vec3.atCenterOf(LEFT_DOOR), Direction.SOUTH, LEFT_DOOR, false);
        check(!player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, ironHit).consumesAction(),
                "Iron door became manually openable");
        assertDoor(level, RIGHT_DOOR, false, "Iron door interaction changed its neighbor");
        fixture(level, oak(DoorHingeSide.LEFT).setValue(DoorBlock.OPEN, true),
                oak(DoorHingeSide.RIGHT).setValue(DoorBlock.OPEN, true).setValue(DoorBlock.POWERED, true));
        toggle(player, LEFT_DOOR);
        assertDoor(level, LEFT_DOOR, false, "Powered-neighbor fixture did not close primary door");
        assertDoor(level, RIGHT_DOOR, true, "Paired helper overrode powered secondary door");
        fixture(level, oak(DoorHingeSide.LEFT).setValue(DoorBlock.OPEN, true).setValue(DoorBlock.POWERED, true),
                oak(DoorHingeSide.RIGHT).setValue(DoorBlock.OPEN, true));
        toggle(player, LEFT_DOOR);
        assertDoor(level, LEFT_DOOR, false, "Powered primary did not keep vanilla manual toggle behavior");
        assertDoor(level, RIGHT_DOOR, true, "Powered primary incorrectly toggled unpowered secondary door");
        fixture(level, oak(DoorHingeSide.LEFT), oak(DoorHingeSide.RIGHT).setValue(DoorBlock.OPEN, true));
        BlockPos powerSource = LEFT_DOOR.north();
        level.setBlock(powerSource, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        check(level.hasNeighborSignal(LEFT_DOOR), "Redstone fixture did not power primary door");
        assertDoor(level, LEFT_DOOR, true, "Redstone source did not open primary door");
        toggle(player, LEFT_DOOR.above());
        assertDoor(level, RIGHT_DOOR, true, "Redstone primary upper-half click changed unpowered partner");
        level.setBlock(powerSource, Blocks.AIR.defaultBlockState(), 3);
    }

    private static void aim(Minecraft client, Vec3 position, Vec3 point) {
        client.player.setPos(position);
        client.player.setDeltaMovement(Vec3.ZERO);
        lookAt(client, point);
    }

    private static void lookAt(Minecraft client, Vec3 point) {
        Vec3 delta = point.subtract(client.player.getEyePosition());
        client.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        client.player.setXRot((float)Math.toDegrees(Math.atan2(-delta.y, Math.hypot(delta.x, delta.z))));
        client.player.yRotO = client.player.getYRot();
        client.player.xRotO = client.player.getXRot();
        try { var pick = Minecraft.class.getDeclaredMethod("pick", float.class); pick.setAccessible(true); pick.invoke(client, 1F); } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void runPlacementRejectionChecks(Minecraft client) {
        var original = client.level.getBlockState(GHOST);
        client.level.setBlock(GHOST, Blocks.STONE.defaultBlockState(), 0);
        aim(client, BRIDGE_POSITION, GHOST_AIM);
        check(PlacementAssist.findTarget(client, 1) == null, "Phantom targeted an occupied cell");
        client.level.setBlock(GHOST, original, 0);

        var wallPos = GHOST.above();
        var wallOriginal = client.level.getBlockState(wallPos);
        client.level.setBlock(wallPos, Blocks.STONE.defaultBlockState(), 0);
        aim(client, BRIDGE_POSITION, GHOST_AIM);
        check(PlacementAssist.findTarget(client, 1) == null, "Phantom placement aimed through a wall");
        client.level.setBlock(wallPos, wallOriginal, 0);

        aim(client, new Vec3(0.5, 80.6, 3.5), new Vec3(0.5, 80.3, 3.5));
        check(PlacementAssist.findTarget(client, 1) == null, "Phantom placement intersects player's body");
        aim(client, new Vec3(0.5, 81, -10.5), GHOST_AIM);
        check(PlacementAssist.findTarget(client, 1) == null, "Phantom placement exceeds vanilla reach");

        aim(client, BRIDGE_POSITION, GHOST_AIM);
        var main = client.player.getMainHandItem();
        var off = client.player.getOffhandItem();
        client.player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BREAD));
        client.player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.COBBLESTONE));
        check(PlacementAssist.findTarget(client, 1) == null, "Block offhand intercepted food mainhand use");
        client.player.setItemSlot(EquipmentSlot.MAINHAND, main);
        client.player.setItemSlot(EquipmentSlot.OFFHAND, off);
        QualityClient.config.placementAssist = false;
        check(PlacementAssist.findTarget(client, 1) == null, "Disabled placement assist still generated target");
        QualityClient.config.placementAssist = true;
    }
}
