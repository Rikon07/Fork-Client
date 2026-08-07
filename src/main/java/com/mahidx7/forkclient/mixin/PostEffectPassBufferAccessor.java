package com.mahidx7.forkclient.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(PostPass.class)
public interface PostEffectPassBufferAccessor {
    @Accessor("customUniforms")
    Map<String, GpuBuffer> forkClient$getUniformBuffers();
}
