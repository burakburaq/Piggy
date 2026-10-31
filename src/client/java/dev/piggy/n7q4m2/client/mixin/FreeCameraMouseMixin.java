package dev.piggy.n7q4m2.client.mixin;
import dev.piggy.n7q4m2.client.quality.movement.FreeCamera;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(MouseHandler.class)
public abstract class FreeCameraMouseMixin {
    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void piggy$cameraTurn(LocalPlayer player, double x, double y) { FreeCamera.turn(player, x, y); }
}
