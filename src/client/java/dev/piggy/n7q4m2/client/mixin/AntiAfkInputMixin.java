package dev.piggy.n7q4m2.client.mixin;

import dev.piggy.n7q4m2.client.quality.movement.AntiAfk;
import dev.piggy.n7q4m2.client.quality.movement.FreeCamera;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class AntiAfkInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("RETURN"))
    private void piggy$antiAfk(CallbackInfo ci) {
        Input next;
        if (FreeCamera.active()) { FreeCamera.move(keyPresses); next = Input.EMPTY; }
        else next = AntiAfk.apply(keyPresses);
        if (next == keyPresses) return;
        keyPresses = next;
        moveVector = new Vec2((next.left() ? 1 : 0) - (next.right() ? 1 : 0),
                (next.forward() ? 1 : 0) - (next.backward() ? 1 : 0)).normalized();
    }
}
