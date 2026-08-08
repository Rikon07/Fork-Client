package com.mahidx7.forkclient.mixin;

import com.mahidx7.forkclient.client.ForkClientController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LightmapRenderStateExtractor.class)
public class LightmapRenderStateExtractorMixin {
    @Unique
    @Redirect(
        method = "extract",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;")
    )
    private Object forkClient$overrideGamma(OptionInstance<Double> instance) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.options != null
                && instance == client.options.gamma()
                && ForkClientController.INSTANCE.isBrightnessPlusEnabled()) {
            return ForkClientController.INSTANCE.getBrightnessPlusValue();
        }
        return instance.get();
    }
}
