package dev.piggy.n7q4m2.quality.doors.mixin;

import dev.piggy.n7q4m2.quality.doors.PairedDoors;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(DoorBlock.class)
public abstract class PairedDoorMixin {
    @WrapMethod(method = "useWithoutItem")
    private InteractionResult piggy$pairAfterSuccessfulToggle(BlockState state, Level level, BlockPos pos, Player player,
                                                              BlockHitResult hit, Operation<InteractionResult> original) {
        // Vanilla reassigns its state argument while toggling; the wrapper retains the original immutable state.
        InteractionResult result = original.call(state, level, pos, player, hit);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && result.consumesAction())
            PairedDoors.afterToggle(level, pos, serverPlayer, state);
        return result;
    }
}
