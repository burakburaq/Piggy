package dev.piggy.n7q4m2.client.quality.inventory.mixin;

import dev.piggy.n7q4m2.client.quality.inventory.InventoryFeatures;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class ShulkerPreviewMixin {
    @Shadow protected Slot hoveredSlot;

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void piggy$previewShulker(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (hoveredSlot != null && hoveredSlot.hasItem()
                && InventoryFeatures.renderShulker(graphics, hoveredSlot.getItem(), mouseX, mouseY)) ci.cancel();
        else InventoryFeatures.clearPreview();
    }
    @Inject(method = "removed", at = @At("HEAD"))
    private void piggy$releasePreview(CallbackInfo ci) {
        InventoryFeatures.clearPreview();
    }
}
