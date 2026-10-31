package dev.piggy.n7q4m2.movement;

import dev.piggy.n7q4m2.network.HopPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;

public final class HopController {
    private HopController() {}
    public static boolean movementHeld(Input input) {
        return input.forward() || input.backward() || input.left() || input.right();
    }
    public static boolean eligible(Player player) {
        return player.isAlive() && !player.isPassenger() && !player.isInWater() && !player.isInLava()
                && !player.onClimbable() && !player.isFallFlying() && !player.getAbilities().flying
                && !player.isSpectator();
    }
    public static boolean movementActive(Player player) {
        HopAccess access = (HopAccess)player;
        return access.piggy$enabled() && eligible(player)
                && !player.isShiftKeyDown();
    }
    public static void sync(Player player) {
        if (player instanceof ServerPlayer serverPlayer
                && ServerPlayNetworking.canSend(serverPlayer, HopPayloads.State.TYPE))
            ServerPlayNetworking.send(serverPlayer, new HopPayloads.State(((HopAccess) player).piggy$state().active()));
    }
    public static void stop(Player player) {
        ((HopAccess) player).piggy$state().reset();
        sync(player);
    }
}
