package dev.piggy.n7q4m2.client.mixin;

import dev.piggy.n7q4m2.client.quality.SeeThroughState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class SeeThroughStateMixin implements SeeThroughState {
    @Unique private boolean piggy$seeThrough;
    public boolean piggy$seeThrough() { return piggy$seeThrough; }
    public void piggy$seeThrough(boolean value) { piggy$seeThrough = value; }
}
