package dev.piggy.n7q4m2.test;

import dev.piggy.n7q4m2.client.quality.QualityClient;
import dev.piggy.n7q4m2.client.quality.inventory.InventoryFeatures;
import dev.piggy.n7q4m2.quality.QualityOfLife;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Actual depletion events on a survival integrated server, plus real shulker rendering. */
public final class QualityInventoryGameTest implements FabricClientGameTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @Override public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("piggy.preparePlaytest")) return;
        boolean[] oldSettings = new boolean[2];
        context.runOnClient(client -> {
            oldSettings[0] = QualityClient.config.autoRestock;
            oldSettings[1] = QualityClient.config.shulkerPreview;
            QualityClient.config.autoRestock = true;
            QualityClient.config.shulkerPreview = true;
        });
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("difficulty peaceful");
            context.waitFor(client -> client.player != null);
            context.runOnClient(client -> QualityClient.sync());
            waitForRestock(context, true);
            // A preference packet can arrive before the client's loaded-world acknowledgement.
            // ServerPlayer deliberately rejects all ordinary damage until that acknowledgement.
            waitForDamageReady(context);

            world.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                check(QualityOfLife.settings(player).autoRestock(), "Restock preference handshake missing");
                testActualToolBreak(player);
                testActualBlockPlacement(player);
                testActualTotemUse(player);
                resetInventory(player);
            });

            // This exercises the preference packet, not a direct server-side preference override.
            context.runOnClient(client -> {
                QualityClient.config.autoRestock = false;
                QualityClient.sync();
            });
            waitForRestock(context, false);
            world.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                resetInventory(player);
                ItemStack broken = nearBrokenAxe();
                player.getInventory().setItem(0, broken);
                player.getInventory().setItem(9, new ItemStack(Items.IRON_AXE));
                broken.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
                check(player.getMainHandItem().isEmpty(), "Disabled helper refilled a broken tool");
                check(player.getInventory().getItem(9).is(Items.IRON_AXE), "Disabled helper moved its spare");
                resetInventory(player);
                player.getInventory().setItem(9, loadedShulker());
                player.inventoryMenu.broadcastChanges();
            });
            context.runOnClient(client -> {
                QualityClient.config.autoRestock = true;
                QualityClient.sync();
            });
            waitForRestock(context, true);
            context.waitFor(client -> client.player.getInventory().getItem(9).is(Items.SHULKER_BOX));
            context.setScreen(PreviewInventoryScreen::new);
            double[] mouse = new double[2];
            context.runOnClient(client -> {
                // Survival inventory slot 9: first slot in the main inventory grid.
                var slot = client.player.inventoryMenu.slots.get(9);
                var screen = (PreviewInventoryScreen) client.gui.screen();
                mouse[0] = (screen.inventoryLeft() + slot.x + 8)
                        * client.getWindow().getWidth() / client.gui.screen().width;
                mouse[1] = (screen.inventoryTop() + slot.y + 8)
                        * client.getWindow().getHeight() / client.gui.screen().height;
            });
            context.getInput().setCursorPos(mouse[0], mouse[1]);
            context.waitTicks(3);
            context.runOnClient(client -> check(((PreviewInventoryScreen) client.gui.screen()).hoveringShulker(),
                    "Shulker screenshot cursor did not actually hover the inventory stack"));
            context.takeScreenshot("quality-shulker-inventory-hover");
            context.setScreen(() -> new PreviewCornerScreen(loadedShulker(), false));
            context.waitTicks(2);
            context.takeScreenshot("quality-shulker-bottom-right");
            context.setScreen(() -> new PreviewCornerScreen(new ItemStack(Items.SHULKER_BOX), true));
            context.waitTicks(2);
            context.takeScreenshot("quality-shulker-empty-top-left");
            context.setScreen(() -> null);
            world.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                resetInventory(player);
                resetDamage(player);
                player.inventoryMenu.broadcastChanges();
            });
        } finally {
            context.setScreen(() -> null);
            context.runOnClient(client -> {
                QualityClient.config.autoRestock = oldSettings[0];
                QualityClient.config.shulkerPreview = oldSettings[1];
                QualityClient.sync();
            });
        }
    }

    private static void waitForRestock(ClientGameTestContext context, boolean enabled) {
        context.waitFor(client -> {
            var server = client.getSingleplayerServer();
            if (server == null || server.getPlayerList().getPlayers().isEmpty()) return false;
            return QualityOfLife.settings(server.getPlayerList().getPlayers().getFirst()).autoRestock() == enabled;
        }, 100);
    }

    private static void waitForDamageReady(ClientGameTestContext context) {
        context.waitFor(client -> {
            var server = client.getSingleplayerServer();
            if (server == null || server.getPlayerList().getPlayers().isEmpty()) return false;
            var player = server.getPlayerList().getPlayers().getFirst();
            return player.connection.hasClientLoaded() && !player.isChangingDimension()
                    && !player.getAbilities().invulnerable && player.isAlive();
        }, 100);
    }

    private static void testActualToolBreak(ServerPlayer player) {
        resetInventory(player);
        ItemStack broken = nearBrokenAxe();
        ItemStack spare = new ItemStack(Items.IRON_AXE);
        spare.set(DataComponents.CUSTOM_NAME, Component.literal("Preserve my components"));
        spare.setDamageValue(23);
        player.getInventory().setItem(0, broken);
        player.getInventory().setItem(9, new ItemStack(Items.DIAMOND_AXE));
        player.getInventory().setItem(10, new ItemStack(Items.IRON_PICKAXE));
        player.getInventory().setItem(3, spare);
        boolean[] callback = { false };
        broken.hurtAndBreak(1, player.level(), player, item -> {
            callback[0] = true;
            player.onEquippedItemBroken(item, InteractionHand.MAIN_HAND.asEquipmentSlot());
        });
        check(callback[0], "Tool fixture never triggered the real break callback");
        check(player.getMainHandItem() == spare, "Same-material hotbar axe did not beat inventory diamond axe");
        check(player.getMainHandItem().getDamageValue() == 23
                && player.getMainHandItem().getHoverName().getString().equals("Preserve my components"),
                "Restock lost spare tool components");
        check(player.getInventory().getSelectedSlot() == 0 && player.getInventory().getItem(3).isEmpty(),
                "Tool restock changed selected slot or duplicated the spare");
        check(player.getInventory().getItem(9).is(Items.DIAMOND_AXE)
                && player.getInventory().getItem(10).is(Items.IRON_PICKAXE), "Tool restock moved the wrong type/material");
    }

    private static void testActualBlockPlacement(ServerPlayer player) {
        resetInventory(player);
        BlockPos support = player.blockPosition().offset(3, 1, 0);
        var oldSupport = player.level().getBlockState(support);
        var oldTarget = player.level().getBlockState(support.above());
        try {
            player.level().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            player.level().setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            ItemStack inventoryStack = new ItemStack(Items.COBBLESTONE, 32);
            inventoryStack.set(DataComponents.CUSTOM_NAME, Component.literal("Matched blocks"));
            ItemStack last = inventoryStack.copyWithCount(1);
            player.getInventory().setItem(0, last);
            player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 64)); // Different components: skip.
            player.getInventory().setItem(10, inventoryStack);
            player.getInventory().setItem(2, inventoryStack.copyWithCount(16));
            var result = place(player, support);
            check(result.consumesAction() && player.level().getBlockState(support.above()).is(Blocks.COBBLESTONE),
                    "Last-block fixture did not actually place a block");
            check(last.isEmpty() && player.getMainHandItem() == inventoryStack && inventoryStack.getCount() == 32,
                    "Exact inventory block stack did not refill selected slot");
            check(player.getInventory().getItem(2).getCount() == 16 && player.getInventory().getItem(9).getCount() == 64,
                    "Block restock ignored component matching or storage priority");

            resetInventory(player);
            player.level().setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE));
            ItemStack hotbarSpare = new ItemStack(Items.COBBLESTONE, 12);
            player.getInventory().setItem(5, hotbarSpare);
            check(place(player, support).consumesAction() && player.getMainHandItem() == hotbarSpare,
                    "Other-hotbar fallback did not refill placed block");
            check(player.getInventory().getItem(5).isEmpty(), "Hotbar block fallback duplicated items");

            resetInventory(player);
            player.level().setBlock(support.above(), Blocks.BEDROCK.defaultBlockState(), 3);
            ItemStack canceled = new ItemStack(Items.COBBLESTONE);
            ItemStack reserve = new ItemStack(Items.COBBLESTONE, 20);
            player.getInventory().setItem(0, canceled);
            player.getInventory().setItem(9, reserve);
            check(!place(player, support).consumesAction(), "Occupied placement fixture was not rejected");
            check(player.getMainHandItem() == canceled && canceled.getCount() == 1
                    && player.getInventory().getItem(9) == reserve, "Rejected placement moved inventory");
            player.getInventory().setItem(0, ItemStack.EMPTY);
            player.inventoryMenu.broadcastChanges();
            check(player.getMainHandItem().isEmpty() && player.getInventory().getItem(9) == reserve,
                    "Empty-hand/menu update triggered unsolicited restock");
        } finally {
            player.level().setBlock(support, oldSupport, 3);
            player.level().setBlock(support.above(), oldTarget, 3);
        }
    }

    private static net.minecraft.world.InteractionResult place(ServerPlayer player, BlockPos support) {
        var hit = new BlockHitResult(Vec3.atCenterOf(support).add(0, .5, 0), Direction.UP, support, false);
        var context = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        return ((BlockItem) player.getMainHandItem().getItem()).place(context);
    }

    private static void testActualTotemUse(ServerPlayer player) {
        resetInventory(player);
        resetDamage(player);
        ItemStack held = new ItemStack(Items.DIAMOND_AXE);
        ItemStack used = new ItemStack(Items.TOTEM_OF_UNDYING);
        ItemStack spare = new ItemStack(Items.TOTEM_OF_UNDYING);
        spare.set(DataComponents.CUSTOM_NAME, Component.literal("Offhand spare"));
        player.getInventory().setItem(0, held);
        player.setItemInHand(InteractionHand.OFF_HAND, used);
        player.getInventory().setItem(9, spare);
        fatalTotemHit(player);
        check(player.isAlive() && used.isEmpty(), "Generic fatal hit did not consume offhand totem");
        check(player.getOffhandItem() == spare && player.getMainHandItem() == held
                && player.getInventory().getItem(9).isEmpty(), "Offhand restock changed wrong hand or duplicated totem");

        resetInventory(player);
        resetDamage(player);
        ItemStack mainTotem = new ItemStack(Items.TOTEM_OF_UNDYING);
        ItemStack untouchedOffhand = new ItemStack(Items.TOTEM_OF_UNDYING);
        ItemStack mainSpare = new ItemStack(Items.TOTEM_OF_UNDYING);
        player.getInventory().setItem(0, mainTotem);
        player.setItemInHand(InteractionHand.OFF_HAND, untouchedOffhand);
        player.getInventory().setItem(11, mainSpare);
        fatalTotemHit(player);
        check(player.isAlive() && mainTotem.isEmpty(), "Mainhand totem priority/consumption failed");
        check(player.getMainHandItem() == mainSpare && player.getOffhandItem() == untouchedOffhand
                && player.getInventory().getItem(11).isEmpty(), "Mainhand restock overwrote offhand or duplicated totem");
        resetDamage(player);
    }

    private static void fatalTotemHit(ServerPlayer player) {
        var source = player.damageSources().generic();
        check(player.connection.hasClientLoaded() && !player.isInvulnerableTo(player.level(), source)
                        && !player.getAbilities().invulnerable,
                "Fatal hit fixture is still immune: clientLoaded=" + player.connection.hasClientLoaded()
                        + ", changingDimension=" + player.isChangingDimension());
        check(player.hurtServer(player.level(), source, 1000), "Fatal generic hit was rejected by vanilla damage handling");
    }

    private static ItemStack nearBrokenAxe() {
        ItemStack stack = new ItemStack(Items.IRON_AXE);
        stack.setDamageValue(stack.getMaxDamage() - 1);
        return stack;
    }

    private static void resetInventory(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) player.getInventory().setItem(slot, ItemStack.EMPTY);
        player.getInventory().setSelectedSlot(0);
    }

    private static void resetDamage(ServerPlayer player) {
        player.removeAllEffects();
        player.setAbsorptionAmount(0);
        player.setInvulnerableTime(0);
        player.setHealth(player.getMaxHealth());
    }

    private static ItemStack loadedShulker() {
        var contents = NonNullList.withSize(27, ItemStack.EMPTY);
        contents.set(0, new ItemStack(Items.DIAMOND, 64));
        contents.set(8, new ItemStack(Items.COBBLESTONE, 17));
        contents.set(13, new ItemStack(Items.IRON_AXE));
        contents.set(26, new ItemStack(Items.FIREWORK_ROCKET, 42));
        ItemStack stack = new ItemStack(Items.SHULKER_BOX);
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        return stack;
    }

    private static final class PreviewCornerScreen extends Screen {
        private final ItemStack stack;
        private final boolean topLeft;
        private PreviewCornerScreen(ItemStack stack, boolean topLeft) {
            super(Component.literal("Shulker corner test"));
            this.stack = stack;
            this.topLeft = topLeft;
        }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(0, 0, width, height, 0xFF20222A);
            check(InventoryFeatures.renderShulker(graphics, stack, topLeft ? 1 : width - 1, topLeft ? 1 : height - 1),
                    "Enabled shulker preview renderer did not handle shulker");
        }
    }

    private static final class PreviewInventoryScreen extends InventoryScreen {
        private PreviewInventoryScreen() { super(Minecraft.getInstance().player); }
        private double inventoryLeft() { return leftPos; }
        private double inventoryTop() { return topPos; }
        private boolean hoveringShulker() {
            return hoveredSlot != null && hoveredSlot.getItem().is(Items.SHULKER_BOX);
        }
    }
}
