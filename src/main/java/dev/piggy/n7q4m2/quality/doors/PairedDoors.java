package dev.piggy.n7q4m2.quality.doors;

import dev.piggy.n7q4m2.quality.QualityOfLife;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Only follows a successful player interaction, never a redstone update. */
public final class PairedDoors {
    private PairedDoors() {}
    public static void initCommon() { /* The DoorBlock mixin supplies the post-interaction hook. */ }

    public static void afterToggle(Level level, BlockPos clicked, ServerPlayer player, BlockState before) {
        if (!QualityOfLife.settings(player).pairedDoors()) return;
        if (!(before.getBlock() instanceof DoorBlock door) || !door.type().canOpenByHand()) return;
        BlockState current = level.getBlockState(clicked);
        if (!current.is(door) || current.getValue(DoorBlock.OPEN).equals(before.getValue(DoorBlock.OPEN))) return;

        BlockPos base = current.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? clicked.below() : clicked;
        BlockState lower = level.getBlockState(base);
        if (!completeDoor(level, base, lower) || lower.getValue(DoorBlock.POWERED)
                || current.getValue(DoorBlock.POWERED)
                || level.hasNeighborSignal(base) || level.hasNeighborSignal(base.above())) return;
        Direction facing = lower.getValue(DoorBlock.FACING);
        Direction partnerDirection = lower.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT
                ? facing.getClockWise() : facing.getCounterClockWise();
        BlockPos partnerPos = base.relative(partnerDirection);
        BlockState partner = level.getBlockState(partnerPos);
        if (!partner.is(door) || !completeDoor(level, partnerPos, partner)
                || partner.getValue(DoorBlock.FACING) != facing
                || partner.getValue(DoorBlock.HINGE) == lower.getValue(DoorBlock.HINGE)
                || partner.getValue(DoorBlock.POWERED)
                || level.hasNeighborSignal(partnerPos) || level.hasNeighborSignal(partnerPos.above())) return;
        if (!level.mayInteract(player, partnerPos) || !level.mayInteract(player, partnerPos.above())
                || !player.isWithinBlockInteractionRange(partnerPos, 0.0)) return;
        // Region/protection mods can veto the secondary interaction just like a normal right click.
        BlockHitResult partnerHit = new BlockHitResult(Vec3.atCenterOf(partnerPos), facing, partnerPos, false);
        if (UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, partnerHit)
                != InteractionResult.PASS) return;
        partner = level.getBlockState(partnerPos);
        if (!partner.is(door) || !completeDoor(level, partnerPos, partner)
                || partner.getValue(DoorBlock.FACING) != facing
                || partner.getValue(DoorBlock.HINGE) == lower.getValue(DoorBlock.HINGE)
                || partner.getValue(DoorBlock.POWERED)
                || level.hasNeighborSignal(partnerPos) || level.hasNeighborSignal(partnerPos.above())) return;

        // setOpen emits the partner's own sound/event; vertical neighbor updates synchronize its upper half.
        boolean open = current.getValue(DoorBlock.OPEN);
        door.setOpen(player, level, partner, partnerPos, open);
        BlockState upper = level.getBlockState(partnerPos.above());
        if (upper.is(door) && upper.getValue(DoorBlock.OPEN) != open)
            level.setBlock(partnerPos.above(), upper.setValue(DoorBlock.OPEN, open), 10);
    }

    private static boolean completeDoor(Level level, BlockPos base, BlockState lower) {
        if (!(lower.getBlock() instanceof DoorBlock) || lower.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) return false;
        BlockState upper = level.getBlockState(base.above());
        return upper.is(lower.getBlock()) && upper.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
                && upper.getValue(DoorBlock.FACING) == lower.getValue(DoorBlock.FACING)
                && upper.getValue(DoorBlock.HINGE) == lower.getValue(DoorBlock.HINGE);
    }
}
