package com.mahidx7.forkclient.mixin;

import com.mahidx7.forkclient.client.ForkClientController;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class CameraMixin {
    @Shadow
    protected void setRotation(float yRot, float xRot) {
    }

    @Unique
    @Inject(method = "update", at = @At("RETURN"))
    private void forkClient$applyFreelook(DeltaTracker deltaTracker, CallbackInfo ci) {
        ForkClientController ctrl = ForkClientController.INSTANCE;
        if (!ctrl.isFreelooking()) {
            return;
        }

        ctrl.updateFreelookMouse();
        this.setRotation(ctrl.getFreelookYaw(), ctrl.getFreelookPitch());
    }
}
