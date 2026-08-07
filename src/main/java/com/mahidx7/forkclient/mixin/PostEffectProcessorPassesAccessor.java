package com.mahidx7.forkclient.mixin;

import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(PostChain.class)
public interface PostEffectProcessorPassesAccessor {
    @Accessor("passes")
    List<PostPass> forkClient$getPasses();
}
