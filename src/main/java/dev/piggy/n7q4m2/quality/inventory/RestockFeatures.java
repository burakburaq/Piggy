package dev.piggy.n7q4m2.quality.inventory;

import dev.piggy.n7q4m2.quality.QualityOfLife;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;

/** Refill only from a verified depletion event, never merely from an empty hand. */
public final class RestockFeatures {
    private RestockFeatures() {}

    public static void initCommon() { /* Consumption hooks are package-specific mixins. */ }

    public record Destination(InteractionHand hand, int selectedSlot, ItemStack consumed) {}

    public static Destination capture(ServerPlayer player, InteractionHand hand, ItemStack consumed) {
        if (!enabled(player) || consumed.isEmpty() || consumed.getCount() != 1
                || player.getItemInHand(hand) != consumed) return null;
        return new Destination(hand, player.getInventory().getSelectedSlot(), consumed);
    }

    public static Destination captureHeld(ServerPlayer player, ItemStack consumed) {
        if (player == null) return null;
        for (InteractionHand hand : InteractionHand.values()) {
            Destination destination = capture(player, hand, consumed);
            if (destination != null) return destination;
        }
        return null;
    }

    public static void blockConsumed(ServerPlayer player, Destination destination, ItemStack original) {
        if (!ready(player, destination)) return;
        int source = findExact(player.getInventory(), destination, original, false);
        transfer(player, destination, source);
    }

    public static void totemConsumed(ServerPlayer player, Destination destination) {
        if (!ready(player, destination)) return;
        int source = findExact(player.getInventory(), destination, ItemStack.EMPTY, true);
        transfer(player, destination, source);
    }

    public static void toolBroken(ServerPlayer player, Destination destination, ItemStack original) {
        if (!ready(player, destination) || !original.isDamageableItem()) return;
        Inventory inventory = player.getInventory();
        var candidates = new ArrayList<RestockSelection.Candidate>();
        String type = toolType(original);
        String material = material(original);
        for (int slot = 0; slot < 36; slot++) {
            if (isDestination(destination, slot)) continue;
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.isDamageableItem() || stack.isBroken() || !toolType(stack).equals(type)) continue;
            String candidateMaterial = material(stack);
            candidates.add(new RestockSelection.Candidate(slot, candidateMaterial.equals(material),
                    RestockSelection.materialStrength(candidateMaterial), stack.getMaxDamage() - stack.getDamageValue()));
        }
        transfer(player, destination, RestockSelection.choose(candidates));
    }

    private static boolean enabled(ServerPlayer player) {
        return player != null && !player.hasInfiniteMaterials() && !player.isSpectator()
                && QualityOfLife.settings(player).autoRestock();
    }

    private static boolean ready(ServerPlayer player, Destination destination) {
        return destination != null && enabled(player) && destination.consumed().isEmpty()
                && player.getItemInHand(destination.hand()).isEmpty()
                && (destination.hand() == InteractionHand.OFF_HAND
                    || player.getInventory().getSelectedSlot() == destination.selectedSlot());
    }

    private static boolean isDestination(Destination destination, int slot) {
        return destination.hand() == InteractionHand.MAIN_HAND && slot == destination.selectedSlot();
    }

    private static int findExact(Inventory inventory, Destination destination, ItemStack original, boolean totem) {
        for (int pass = 0; pass < 2; pass++) {
            int first = pass == 0 ? 9 : 0;
            int last = pass == 0 ? 36 : 9;
            for (int slot = first; slot < last; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!isDestination(destination, slot) && !stack.isEmpty()
                        && (totem ? stack.is(Items.TOTEM_OF_UNDYING) : ItemStack.isSameItemSameComponents(original, stack))) return slot;
            }
        }
        return -1;
    }

    private static void transfer(ServerPlayer player, Destination destination, int source) {
        if (source < 0 || !ready(player, destination)) return;
        Inventory inventory = player.getInventory();
        int target = destination.hand() == InteractionHand.OFF_HAND ? Inventory.SLOT_OFFHAND : destination.selectedSlot();
        ItemStack replacement = inventory.getItem(source);
        if (replacement.isEmpty() || !inventory.getItem(target).isEmpty()) return;
        inventory.setItem(source, ItemStack.EMPTY);
        inventory.setItem(target, replacement);
        inventory.setChanged();
        player.connection.send(inventory.createInventoryUpdatePacket(source));
        player.connection.send(inventory.createInventoryUpdatePacket(target));
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
    }

    private static String toolType(ItemStack stack) {
        if (stack.is(ItemTags.AXES)) return "axe";
        if (stack.is(ItemTags.PICKAXES)) return "pickaxe";
        if (stack.is(ItemTags.SHOVELS)) return "shovel";
        if (stack.is(ItemTags.HOES)) return "hoe";
        if (stack.is(ItemTags.SWORDS)) return "sword";
        if (stack.is(ItemTags.SPEARS)) return "spear";
        // Bows, shears, rods and other un-tiered tools require the identical item.
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String material(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id.getPath();
        int separator = path.indexOf('_');
        String prefix = separator < 0 ? path : path.substring(0, separator);
        return id.getNamespace().equals("minecraft") && RestockSelection.materialStrength(prefix) > 0
                ? prefix : id.toString();
    }
}
