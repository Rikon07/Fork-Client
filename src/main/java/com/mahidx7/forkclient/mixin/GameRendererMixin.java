package com.mahidx7.forkclient.mixin;

import com.mahidx7.forkclient.client.ForkClientController;
import com.mahidx7.forkclient.client.modules.MotionBlurPlusRenderer;
import com.mahidx7.forkclient.client.permissions.FeaturePermissions;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Unique
    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void forkClient$forceBlockOutline(CallbackInfoReturnable<Boolean> cir) {
        if (ForkClientController.INSTANCE.isModuleEnabled("block_outline") && FeaturePermissions.canUseRendering()) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void forkClient$disableHurtTilt(CameraRenderState cameraRenderState, PoseStack poseStack, CallbackInfo ci) {
        if (ForkClientController.INSTANCE.isModuleEnabled("no_hurt_camera") && FeaturePermissions.canUseRendering()) {
            ci.cancel();
        }
    }

    @Unique
    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/resource/CrossFrameResourcePool;endFrame()V"
        )
    )
    private void forkClient$applyMotionBlurPlus(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
        MotionBlurPlusRenderer.render();
    }
}
