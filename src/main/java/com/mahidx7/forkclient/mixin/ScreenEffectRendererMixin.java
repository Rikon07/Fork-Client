package com.mahidx7.forkclient.mixin;

import com.mahidx7.forkclient.client.ForkClientController;
import com.mahidx7.forkclient.client.permissions.FeaturePermissions;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {
    @Unique
    private static int forkClient$fireTransformDepth;

    @Unique
    @Inject(method = "submitFire", at = @At("RETURN"))
    private static void forkClient$lowerFire(PoseStack poseStack, SubmitNodeCollector bufferSource, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (forkClient$fireTransformDepth > 0) {
            poseStack.popPose();
            forkClient$fireTransformDepth--;
        }
    }

    @Unique
    @Inject(method = "submitFire", at = @At("HEAD"))
    private static void forkClient$applyFireTransform(PoseStack poseStack, SubmitNodeCollector bufferSource, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (ForkClientController.INSTANCE.isModuleEnabled("low_fire") && FeaturePermissions.canUseRendering()) {
            poseStack.pushPose();
            poseStack.translate(0.0, 1.2, 0.0);
            poseStack.scale(1.0F, 0.3F, 1.0F);
            forkClient$fireTransformDepth++;
        }
    }
}
