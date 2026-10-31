package dev.piggy.n7q4m2.client.quality.inventory;

import dev.piggy.n7q4m2.client.quality.QualityClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.level.Level;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SolidBucketItem;

/** Select another hotbar bucket only after vanilla has applied the used hand's item replacement. */
public final class BucketCycle {
    private static Use current, pending;
    private static LocalPlayer lockedPlayer;
    private static Level lockedLevel;
    private static long releases;
    private static final class Use {
        final LocalPlayer player;
        final int slot, count, expires;
        final Item item;
        final long releaseId;
        boolean consumed;
        Use(LocalPlayer player) {
            this.player = player; slot = player.getInventory().getSelectedSlot();
            var stack = player.getMainHandItem(); item = stack.getItem(); count = stack.getCount();
            expires = player.tickCount + 20; releaseId = releases;
        }
    }
    private BucketCycle() {}
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(BucketCycle::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { current = null; pending = null; unlock(); });
    }
    private static void unlock() { lockedPlayer = null; lockedLevel = null; }
    public static void keyReleased(KeyMapping key) {
        var client = Minecraft.getInstance();
        if (client.options != null && key == client.options.keyUse) { releases++; unlock(); }
    }
    public static boolean blocked(Minecraft client) {
        if (lockedPlayer != null && (!QualityClient.config.bucketCycle || !client.options.keyUse.isDown()
                || client.player != lockedPlayer || client.level != lockedLevel || !lockedPlayer.isAlive()
                || client.gui.screen() != null || client.getCameraEntity() != lockedPlayer)) unlock();
        return lockedPlayer != null;
    }
    private static boolean placeableBucket(ItemStack stack) {
        return stack.getItem() instanceof BucketItem || stack.getItem() instanceof SolidBucketItem;
    }
    public static void beginUse(Minecraft client) {
        current = QualityClient.config.bucketCycle && client.player != null && client.getCameraEntity() == client.player
                && placeableBucket(client.player.getMainHandItem()) ? new Use(client.player) : null;
    }
    public static void result(InteractionResult result) {
        if (current != null && result.consumesAction()) current.consumed = true;
    }
    public static void endUse(Minecraft client) {
        if (current != null && current.consumed) pending = current;
        current = null;
        tick(client);
    }
    private static void tick(Minecraft client) {
        blocked(client);
        Use use = pending;
        if (use == null) return;
        if (!QualityClient.config.bucketCycle || client.player != use.player || client.level != use.player.level()
                || !use.player.isAlive() || client.gui.screen() != null || client.getCameraEntity() != use.player
                || use.player.tickCount > use.expires || use.player.getInventory().getSelectedSlot() != use.slot) {
            pending = null; return;
        }
        var held = use.player.getMainHandItem();
        Item wanted;
        if (use.item == Items.BUCKET && !held.is(Items.BUCKET)
                && (placeableBucket(held) || held.is(Items.MILK_BUCKET))) wanted = Items.BUCKET;
        else if (use.item != Items.BUCKET && held.is(Items.BUCKET)) wanted = use.item;
        else {
            // A remaining empty-bucket stack is already usable. Wait briefly only for delayed server updates.
            if (!held.is(use.item) || held.getCount() < use.count) pending = null;
            return;
        }
        pending = null;
        var inventory = use.player.getInventory();
        for (int offset = 1; offset < 9; offset++) {
            int slot = (use.slot + offset) % 9;
            if (inventory.getItem(slot).is(wanted)) {
                inventory.setSelectedSlot(slot);
                return; // Vanilla synchronizes the new selection before the next interaction.
            }
        }
        // Do not reverse the last conversion while the same physical right-click remains held.
        if (use.releaseId == releases && client.options.keyUse.isDown()) {
            lockedPlayer = use.player; lockedLevel = client.level;
        }
    }
}
