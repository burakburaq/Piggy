package dev.piggy.n7q4m2.client.quality.placement.mixin;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BlockItem.class)
public interface BlockItemPlacementAccessor {
    @Invoker("getPlacementState")
    BlockState piggy$getPlacementState(BlockPlaceContext context);
}
