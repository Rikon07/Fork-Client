package com.mahidx7.forkclient.mixin;

import com.mahidx7.forkclient.client.ForkClientController;
import com.mahidx7.forkclient.client.permissions.FeaturePermissions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
    @Unique
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void forkClient$hideLocalNameTag(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (!ForkClientController.INSTANCE.isModuleEnabled("clean_view")) {
            return;
        }
        if (!FeaturePermissions.canUseRendering()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        if (entity == client.player && !client.options.getCameraType().isFirstPerson()) {
            cir.setReturnValue(false);
        }
    }
}
