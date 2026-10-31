package dev.piggy.n7q4m2.test.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface RightClickTestAccessor {
    @Accessor("rightClickDelay") int piggy$delay();
    @Accessor("rightClickDelay") void piggy$delay(int value);
    @Invoker("startUseItem") void piggy$use();
}
