package com.mahidx7.forkclient.client.modules;

import com.mahidx7.forkclient.ForkClient;
import com.mahidx7.forkclient.mixin.GameRendererPoolAccessor;
import com.mahidx7.forkclient.mixin.PostEffectPassBufferAccessor;
import com.mahidx7.forkclient.mixin.PostEffectProcessorPassesAccessor;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Applies the Motion Blur Plus temporal frame blend by processing the
 * {@code fork-client:frame_blend} post effect, which keeps a persistent
 * "history" target across frames. The blend weight is uploaded each frame
 * through a dedicated GPU uniform buffer bound to the effect's
 * {@code BlurPlusParams} uniform block.
 */
public final class MotionBlurPlusRenderer {
    private static final Identifier EFFECT_ID = Identifier.fromNamespaceAndPath("fork-client", "frame_blend");
    private static final String PARAMETER_BLOCK = "BlurPlusParams";

    private static final int PARAMETER_BUFFER_USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE;
    private static final long PARAMETER_BUFFER_SIZE = 16L;

    private static PostChain loadedEffect;
    private static GpuBuffer parameterBuffer;
    private static boolean historyReady;
    private static boolean loadFailureLogged;
    private static int knownWidth;
    private static int knownHeight;

    private MotionBlurPlusRenderer() {
    }

    public static void render() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            resetHistory();
            return;
        }
        if (!MotionBlurPlusModule.isEnabled()) {
            resetHistory();
            return;
        }
        int strength = MotionBlurPlusModule.getStrength();
        if (strength <= 0) {
            resetHistory();
            return;
        }
        PostChain effect = prepareEffect(client);
        if (effect == null || parameterBuffer == null) {
            return;
        }
        RenderTarget mainFramebuffer = client.gameRenderer.mainRenderTarget();
        if (mainFramebuffer.width != knownWidth || mainFramebuffer.height != knownHeight) {
            knownWidth = mainFramebuffer.width;
            knownHeight = mainFramebuffer.height;
            resetTemporalState();
        }
        float historyWeight = historyReady ? (float) Math.min(strength, 98) / 100.0F : 0.0F;
        uploadHistoryWeight(historyWeight);
        CrossFrameResourcePool pool = ((GameRendererPoolAccessor) client.gameRenderer).forkClient$getPool();
        FrameGraphBuilder frame = new FrameGraphBuilder();
        PostChain.TargetBundle targets = PostChain.TargetBundle.of(
                PostChain.MAIN_TARGET_ID, frame.importExternal("main", mainFramebuffer));
        effect.addToFrame(frame, mainFramebuffer.width, mainFramebuffer.height, targets);
        frame.execute(pool);
        historyReady = true;
    }

    public static void resetHistory() {
        knownWidth = -1;
        knownHeight = -1;
        resetTemporalState();
    }

    public static void invalidate() {
        loadedEffect = null;
        if (parameterBuffer != null) {
            parameterBuffer.close();
            parameterBuffer = null;
        }
        loadFailureLogged = false;
        resetHistory();
    }

    private static PostChain prepareEffect(Minecraft client) {
        if (parameterBuffer != null && parameterBuffer.isClosed()) {
            invalidate();
        }
        if (loadedEffect != null && parameterBuffer != null) {
            return loadedEffect;
        }
        try {
            loadedEffect = Objects.requireNonNull(
                    client.getShaderManager().getPostChain(EFFECT_ID, Set.of(LevelTargetBundle.MAIN_TARGET_ID)));
            parameterBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "fork-client motion blur parameters", PARAMETER_BUFFER_USAGE, PARAMETER_BUFFER_SIZE);
            bindParameterBuffer(loadedEffect, parameterBuffer);
            loadFailureLogged = false;
            return loadedEffect;
        } catch (RuntimeException exception) {
            if (!loadFailureLogged) {
                ForkClient.LOGGER.error("Unable to load Motion Blur Plus post effect", exception);
                loadFailureLogged = true;
            }
            return null;
        }
    }

    private static void bindParameterBuffer(PostChain effect, GpuBuffer buffer) {
        for (PostPass pass : ((PostEffectProcessorPassesAccessor) effect).forkClient$getPasses()) {
            Map<String, GpuBuffer> uniformBuffers = ((PostEffectPassBufferAccessor) pass).forkClient$getUniformBuffers();
            if (!uniformBuffers.containsKey(PARAMETER_BLOCK)) {
                continue;
            }
            GpuBuffer previous = uniformBuffers.put(PARAMETER_BLOCK, buffer);
            if (previous != null && previous != buffer) {
                previous.close();
            }
        }
    }

    private static void uploadHistoryWeight(float weight) {
        try (GpuBufferSlice.MappedView mappedView = parameterBuffer.map(false, true)) {
            Std140Builder.intoBuffer(mappedView.data()).putFloat(weight);
        }
    }

    private static void resetTemporalState() {
        historyReady = false;
    }
}
