package dev.piggy.n7q4m2.client.quality.placement.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.piggy.n7q4m2.client.quality.placement.PlacementAssist;
import dev.piggy.n7q4m2.client.quality.placement.UseTiming;
import dev.piggy.n7q4m2.client.quality.placement.PlantingUse;
import dev.piggy.n7q4m2.client.quality.inventory.BucketCycle;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class PlacementUseMixin {
    @Shadow private int rightClickDelay;
    @Unique private boolean piggy$fireworkWaiting;
    @Unique private long piggy$fireworkRelease;

    @Inject(method = "tick", at = @At("HEAD"))
    private void piggy$releaseBridge(CallbackInfo ci) {
        Minecraft mc = (Minecraft)(Object)this;
        if (piggy$fireworkWaiting) {
            if (UseTiming.useReleases() != piggy$fireworkRelease || !mc.options.keyUse.isDown()
                    || mc.player == null || !mc.player.isAlive() || mc.gui.screen() != null
                    || !mc.player.getMainHandItem().is(Items.FIREWORK_ROCKET) && !mc.player.getOffhandItem().is(Items.FIREWORK_ROCKET)) {
                rightClickDelay = 0; piggy$fireworkWaiting = false;
            } else if (rightClickDelay <= 0) piggy$fireworkWaiting = false;
        }
        PlacementAssist.refreshUseLatch(mc);
        PlantingUse.refresh(mc);
    }
    @WrapMethod(method = "startUseItem")
    private void piggy$usePhantom(Operation<Void> original) {
        Minecraft mc = (Minecraft)(Object)this;
        piggy$fireworkWaiting = false;
        if (BucketCycle.blocked(mc)) { rightClickDelay = Math.max(rightClickDelay, 1); return; }
        PlacementAssist.refreshUseLatch(mc);
        PlacementAssist.Target ghost = PlacementAssist.findTarget(mc, 1.0F);
        if (ghost != null || PlacementAssist.bridging()) {
            rightClickDelay = 2;
            // A latched bridge never falls through to vanilla use, even when aim or held item changes.
            if (ghost == null || mc.gameMode == null || mc.gameMode.isDestroying() || mc.player.isUsingItem()) return;
            var animation = mc.player.getItemInHand(ghost.hand()).getInteractAnimation();
            InteractionResult result = mc.gameMode.useItemOn(mc.player, ghost.hand(), ghost.anchorHit());
            if (result instanceof InteractionResult.Success success) {
                PlacementAssist.beginBridge(mc, ghost);
                if (success.swingSource() == InteractionResult.SwingSource.PREDICTED) mc.player.swing(ghost.hand(), animation, false);
                mc.player.itemUsed(ghost.hand());
            }
            return;
        }
        long placementsBefore = UseTiming.placements();
        long waterBefore = UseTiming.waterUses();
        long fireworksBefore = UseTiming.fireworkUses();
        BucketCycle.beginUse(mc);
        try { original.call(); } finally { BucketCycle.endUse(mc); }
        if (UseTiming.fireworkUses() != fireworksBefore && mc.gui.screen() == null) {
            rightClickDelay = UseTiming.fireworkInterval();
            piggy$fireworkWaiting = true; piggy$fireworkRelease = UseTiming.useReleases();
            return;
        }
        // Water uses four ticks; real block placements get two ticks and other uses one tick.
        // Vanilla still controls item-use durations, item cooldowns, screens and server validation.
        if (mc.gui.screen() == null) rightClickDelay = Math.min(rightClickDelay,
                UseTiming.waterUses() != waterBefore ? 4 : UseTiming.placements() != placementsBefore ? 2 : 1);
    }
}
