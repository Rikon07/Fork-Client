package com.mahidx7.forkclient.mixin;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererPoolAccessor {
    @Accessor("resourcePool")
    CrossFrameResourcePool forkClient$getPool();
}
