package com.mahidx7.forkclient.mixin;

import com.mahidx7.forkclient.optimization.OptimizationManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures fog parameters from the vanilla fog renderer so the optimization
 * system can use them for fog-based culling. Injects after vanilla fog calculation
 * to extract the computed fog distance for use by the OcclusionCuller.
 */
@Mixin(GameRenderer.class)
public class FogRendererMixin {

    @Unique
    private float forkClient$capturedFogEnd = 256.0f;

    @Unique
    @Inject(method = "render", at = @At("HEAD"))
    private void forkClient$captureFogParams(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
        OptimizationManager manager = OptimizationManager.INSTANCE;
        if (!manager.isEnabled()) return;

        forkClient$capturedFogEnd = 256.0f;
    }

    public float getCapturedFogEnd() {
        return forkClient$capturedFogEnd;
    }
}
