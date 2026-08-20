package com.mahidx7.forkclient.mixin;

import com.mahidx7.forkclient.client.modules.EntityCullingModule;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    /**
     * Inject at the HEAD of {@code submitEntities} to kick off async ray-trace
     * tasks for every visible entity and swap the double-buffered results so the
     * current frame can read last frame's occlusion data.
     */
    @Unique
    @Inject(method = "submitEntities", at = @At("HEAD"))
    private void forkClient$initEntityCulling(
            PoseStack poseStack,
            LevelRenderState levelRenderState,
            SubmitNodeCollector output,
            CallbackInfo ci
    ) {
        if (!EntityCullingModule.isEnabled()) {
            return;
        }
        EntityCullingModule.update(
                levelRenderState.entityRenderStates,
                levelRenderState.cameraRenderState.pos
        );
    }

    /**
     * Redirect every {@code entityRenderDispatcher.submit(...)} call inside
     * {@code submitEntities} so we can skip rendering for entities that the
     * async path-tracer has determined to be fully occluded.
     */
    @Unique
    @Redirect(
            method = "submitEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"
            )
    )
    private void forkClient$cullEntity(
            EntityRenderDispatcher dispatcher,
            EntityRenderState state,
            CameraRenderState camera,
            double x, double y, double z,
            PoseStack poseStack,
            SubmitNodeCollector output
    ) {
        if (EntityCullingModule.isEnabled() && EntityCullingModule.shouldSkip(state)) {
            return;
        }
        dispatcher.submit(state, camera, x, y, z, poseStack, output);
    }
}
