package dev.piggy.n7q4m2.client.quality.inventory;

import dev.piggy.n7q4m2.client.quality.QualityClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class InventoryFeatures {
    public static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath("piggy", "textures/gui/shulker_preview.png");
    private static ItemContainerContents cachedContainer;
    private static final NonNullList<ItemStack> CONTENTS = NonNullList.withSize(27, ItemStack.EMPTY);
    private InventoryFeatures() {}

    public static void initClient() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearPreview());
    }
    public static void clearPreview() {
        if (cachedContainer == null) return;
        cachedContainer = null; CONTENTS.clear();
    }

    public static boolean renderShulker(GuiGraphicsExtractor graphics, ItemStack stack, int mouseX, int mouseY) {
        if (!QualityClient.config.shulkerPreview || !(stack.getItem() instanceof BlockItem item)
                || !(item.getBlock() instanceof ShulkerBoxBlock)) { clearPreview(); return false; }

        Font font = Minecraft.getInstance().font;
        var container = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (cachedContainer != container) {
            container.copyInto(CONTENTS);
            cachedContainer = container;
        }
        int width = 174, height = 76;
        var position = DefaultTooltipPositioner.INSTANCE.positionTooltip(graphics.guiWidth(), graphics.guiHeight(),
                mouseX, mouseY, width, height);
        int x = Math.max(2, Math.min(graphics.guiWidth() - width - 2, position.x()));
        int y = Math.max(2, Math.min(graphics.guiHeight() - height - 2, position.y()));
        graphics.nextStratum();
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0, 0,
                width, height, 174, 76, 174, 76);
        graphics.text(font, font.plainSubstrByWidth(stack.getHoverName().getString(), 162), x + 6, y + 4, 0xFFFFFFFF);
        // Slots and items are drawn above the inventory surface.
        for (int slot = 0; slot < 27; slot++) {
            int sx = x + 6 + (slot % 9) * 18;
            int sy = y + 17 + (slot / 9) * 18;
            graphics.fill(sx, sy, sx + 18, sy + 18, 0x66302816);
            graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x33201610);
            ItemStack itemStack = CONTENTS.get(slot);
            if (!itemStack.isEmpty()) {
                graphics.item(itemStack, sx + 1, sy + 1);
                graphics.itemDecorations(font, itemStack, sx + 1, sy + 1);
            }
        }
        return true;
    }
}
