package dev.piggy.n7q4m2.client.quality.placement;

import dev.piggy.n7q4m2.client.quality.placement.mixin.BlockItemPlacementAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** A successful edible planting owns this held-use gesture until the physical key is released. */
public final class PlantingUse {
    private static LocalPlayer planter;
    private static Level level;
    private PlantingUse() {}
    private static void clear() { planter = null; level = null; }
    public static void keyReleased(KeyMapping key) {
        var mc = Minecraft.getInstance();
        if (mc.options != null && key == mc.options.keyUse) clear();
    }
    public static void refresh(Minecraft mc) {
        if (planter != null && (mc.player != planter || mc.level != level || !mc.options.keyUse.isDown()
                || mc.gui.screen() != null || !planter.isAlive() || mc.getCameraEntity() != planter)) clear();
    }
    public static void planted(BlockPlaceContext context) {
        if (!context.getLevel().isClientSide() || !food(context.getItemInHand())) return;
        var mc = Minecraft.getInstance();
        if (context.getPlayer() == mc.player && mc.player != null && mc.options.keyUse.isDown()
                && mc.gui.screen() == null && mc.getCameraEntity() == mc.player) {
            planter = mc.player; level = mc.level;
        }
    }
    private static boolean food(ItemStack stack) {
        return stack.has(DataComponents.FOOD) && stack.has(DataComponents.CONSUMABLE);
    }
    public static boolean blocksEating(Player player, InteractionHand hand) {
        if (planter == null || player != planter || !player.level().isClientSide()) return false;
        refresh(Minecraft.getInstance());
        return planter != null && food(player.getItemInHand(hand));
    }
    public static boolean blocksFailedPlanting(LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
        if (!blocksEating(player, hand)) return false;
        var stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof BlockItem item)) return false;
        // Do not send a predictably failed planting to an unmodded server: BlockItem.useOn
        // would otherwise fall back to eating there as well as on the client.
        var context = item.updatePlacementContext(new BlockPlaceContext(player, hand, stack, hit));
        return context == null || !context.canPlace()
                || ((BlockItemPlacementAccessor)item).piggy$getPlacementState(context) == null;
    }
}
