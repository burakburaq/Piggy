package dev.piggy.n7q4m2.client.quality.hud;

import dev.piggy.n7q4m2.quality.metrics.OrderedGroups;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.Fireworks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class AmmoGroups {
    private AmmoGroups() {}
    public record RocketGroup(int flight, ItemStack icon, int count) {}

    public static List<OrderedGroups.Group<ItemStack>> arrows(Player player, ItemStack weapon) {
        if (!(weapon.getItem() instanceof ProjectileWeaponItem projectile)) return List.of();
        List<ItemStack> ordered = new ArrayList<>();
        ItemStack offhand = player.getOffhandItem();
        ItemStack mainhand = player.getMainHandItem();
        // Mirrors Player.getProjectile: held projectiles first, then inventory indices.
        boolean offIncluded = offhand.is(ItemTags.ARROWS) && projectile.getSupportedHeldProjectiles().test(offhand);
        boolean mainIncluded = mainhand.is(ItemTags.ARROWS) && projectile.getSupportedHeldProjectiles().test(mainhand);
        if (offIncluded) ordered.add(offhand);
        if (mainIncluded) ordered.add(mainhand);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if ((offIncluded && stack == offhand) || (mainIncluded && stack == mainhand)) continue;
            if (!stack.isEmpty() && stack.is(ItemTags.ARROWS) && projectile.getAllSupportedProjectiles().test(stack))
                ordered.add(stack);
        }
        return OrderedGroups.group(ordered, ItemStack::isSameItemSameComponents, ItemStack::getCount);
    }

    public static List<RocketGroup> rockets(Player player) {
        Map<Integer, RocketGroup> groups = new TreeMap<>();
        // Show the three standard flight-duration totals even when one is empty.
        for (int flight = 1; flight <= 3; flight++) {
            ItemStack icon = new ItemStack(Items.FIREWORK_ROCKET);
            icon.set(DataComponents.FIREWORKS, new Fireworks(flight, List.of()));
            groups.put(flight, new RocketGroup(flight, icon, 0));
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(Items.FIREWORK_ROCKET)) continue;
            int flight = stack.getOrDefault(DataComponents.FIREWORKS, new Fireworks(0, List.of())).flightDuration();
            if (flight < 1 || flight > 3) continue;
            RocketGroup old = groups.get(flight);
            groups.put(flight, new RocketGroup(flight, stack.copyWithCount(1),
                    stack.getCount() + (old == null ? 0 : old.count())));
        }
        return List.copyOf(groups.values());
    }
}
