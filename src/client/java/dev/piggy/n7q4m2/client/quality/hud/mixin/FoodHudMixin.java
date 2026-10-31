package dev.piggy.n7q4m2.client.quality.hud.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.piggy.n7q4m2.client.quality.hud.FoodHud;
import dev.piggy.n7q4m2.client.quality.hud.ClientMetrics;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class FoodHudMixin {
    @Unique private final FoodHud.Cells piggy$foodCells = new FoodHud.Cells();
    @Unique private final FoodHud.Cells piggy$heartCells = new FoodHud.Cells();
    @Unique private int piggy$heartIndex;
    @Unique private int piggy$foodRight;
    @Unique private boolean piggy$foodEnabled, piggy$heartsEnabled;
    @Inject(method = "extractFood", at = @At("HEAD"))
    private void piggy$beginFood(GuiGraphicsExtractor graphics, Player player, int y, int right, CallbackInfo ci) {
        piggy$foodCells.clear();
        piggy$foodEnabled = FoodHud.enabled(player) && ClientMetrics.saturation() >= 0;
        piggy$foodRight = right;
    }
    @WrapOperation(method = "extractFood", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V", ordinal = 0))
    private void piggy$foodCell(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h,
                                  Operation<Void> original, @Local(argsOnly = true) Player player) {
        original.call(graphics, pipeline, sprite, x, y, w, h);
        if (!piggy$foodEnabled) return;
        // Vanilla's food index zero is the rightmost cell, unlike the health bar.
        var cell = piggy$foodCells.append((piggy$foodRight - x - 9) / 8, x, y);
        FoodHud.renderHungerCell(graphics, player, cell);
    }
    @Inject(method = "extractFood", at = @At("RETURN"))
    private void piggy$decorateFood(GuiGraphicsExtractor graphics, Player player, int y, int right, CallbackInfo ci) {
        if (piggy$foodEnabled) FoodHud.renderFood(graphics, player, piggy$foodCells);
    }
    @Inject(method = "extractHearts", at = @At("HEAD"))
    private void piggy$beginHearts(GuiGraphicsExtractor graphics, Player player, int x, int y, int spacing, int bounce,
            float max, int health, int displayed, int absorption, boolean blink, CallbackInfo ci) {
        piggy$heartCells.clear();
        piggy$heartsEnabled = FoodHud.enabled(player) && player.getHealth() < player.getMaxHealth()
                && FoodHud.prediction(player, FoodHud.heldFood(player)) != null;
        piggy$heartIndex = (int)Math.ceil(max / 2) + (int)Math.ceil(absorption / 2.0) - 1;
    }
    @WrapOperation(method = "extractHeart", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void piggy$heartCell(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, Operation<Void> original) {
        original.call(graphics, pipeline, sprite, x, y, w, h);
        if (piggy$heartsEnabled && sprite.getPath().startsWith("hud/heart/container"))
            piggy$heartCells.append(piggy$heartIndex--, x, y);
    }
    @Inject(method = "extractHearts", at = @At("RETURN"))
    private void piggy$decorateHearts(GuiGraphicsExtractor graphics, Player player, int x, int y, int spacing, int bounce,
            float max, int health, int displayed, int absorption, boolean blink, CallbackInfo ci) {
        if (piggy$heartsEnabled) FoodHud.renderHearts(graphics, player, piggy$heartCells);
    }
}
