package com.mahidx7.forkclient.mixin;

import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ShaderManager.class)
public interface ShaderManagerAccessor {
    @Accessor("postChainProjection")
    Projection getPostChainProjection();

    @Accessor("postChainProjectionMatrixBuffer")
    ProjectionMatrixBuffer getPostChainProjectionMatrixBuffer();
}
