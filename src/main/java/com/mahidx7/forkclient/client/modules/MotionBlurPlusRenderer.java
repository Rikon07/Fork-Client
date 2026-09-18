package com.mahidx7.forkclient.client.modules;

import com.mahidx7.forkclient.ForkClient;
import com.mahidx7.forkclient.mixin.GameRendererPoolAccessor;
import com.mahidx7.forkclient.mixin.PostEffectPassBufferAccessor;
import com.mahidx7.forkclient.mixin.PostEffectProcessorPassesAccessor;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

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

    private static final int PARAMETER_BUFFER_USAGE = resolveUsage("USAGE_UNIFORM") | resolveUsage("USAGE_MAP_WRITE");
    private static final long PARAMETER_BUFFER_SIZE = 16L;

    private static PostChain loadedEffect;
    private static Object parameterBuffer;
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
            closeQuietly(parameterBuffer);
            parameterBuffer = null;
        }
        loadFailureLogged = false;
        resetHistory();
    }

    private static PostChain prepareEffect(Minecraft client) {
        if (parameterBuffer != null && isClosed(parameterBuffer)) {
            invalidate();
        }
        if (loadedEffect != null && parameterBuffer != null) {
            return loadedEffect;
        }
        try {
            loadedEffect = Objects.requireNonNull(
                    client.getShaderManager().getPostChain(EFFECT_ID, Set.of(LevelTargetBundle.MAIN_TARGET_ID)));
            parameterBuffer = createParameterBuffer();
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

    private static void bindParameterBuffer(PostChain effect, Object buffer) {
        for (PostPass pass : ((PostEffectProcessorPassesAccessor) effect).forkClient$getPasses()) {
            Map<String, Object> uniformBuffers = ((PostEffectPassBufferAccessor) pass).forkClient$getUniformBuffers();
            if (!uniformBuffers.containsKey(PARAMETER_BLOCK)) {
                continue;
            }
            Object previous = uniformBuffers.put(PARAMETER_BLOCK, buffer);
            if (previous != null && previous != buffer) {
                closeQuietly(previous);
            }
        }
    }

    private static void uploadHistoryWeight(float weight) {
        Object mappedView = invoke(parameterBuffer, "map", false, true);
        try {
            ByteBuffer data = (ByteBuffer) invoke(mappedView, "data");
            Std140Builder.intoBuffer(data).putFloat(weight);
        } catch (RuntimeException e) {
            ForkClient.LOGGER.error("Unable to upload motion blur parameters", e);
        } finally {
            closeQuietly(mappedView);
        }
    }

    private static Object createParameterBuffer() {
        try {
            Object device = RenderSystem.getDevice();
            Method createBuffer = device.getClass().getMethod("createBuffer", Supplier.class, int.class, long.class);
            return createBuffer.invoke(device, (Supplier<String>) () -> "fork-client motion blur parameters",
                    PARAMETER_BUFFER_USAGE, PARAMETER_BUFFER_SIZE);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to create motion blur parameter buffer", e);
        }
    }

    private static boolean isClosed(Object buffer) {
        try {
            return (boolean) buffer.getClass().getMethod("isClosed").invoke(buffer);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static void closeQuietly(Object buffer) {
        try {
            if (buffer != null) {
                Method close = buffer.getClass().getMethod("close");
                close.invoke(buffer);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static int resolveUsage(String constantName) {
        try {
            Class<?> gpuBufferClass = Class.forName("com.mojang.renderpearl.api.buffers.GpuBuffer");
            Field usageField = gpuBufferClass.getField(constantName);
            return usageField.getInt(null);
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    private static Object invoke(Object target, String methodName, Object... args) {
        if (target == null) {
            return null;
        }
        Class<?>[] parameterTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            parameterTypes[i] = args[i] == null ? Object.class : primitiveType(args[i].getClass());
        }
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke " + methodName + " on " + target.getClass().getName(), e);
        }
    }

    private static Class<?> primitiveType(Class<?> type) {
        if (Boolean.class.equals(type)) return boolean.class;
        if (Byte.class.equals(type)) return byte.class;
        if (Short.class.equals(type)) return short.class;
        if (Integer.class.equals(type)) return int.class;
        if (Long.class.equals(type)) return long.class;
        if (Float.class.equals(type)) return float.class;
        if (Double.class.equals(type)) return double.class;
        if (Character.class.equals(type)) return char.class;
        return type;
    }

    private static void resetTemporalState() {
        historyReady = false;
    }
}
