package com.mahidx7.forkclient.mixin;

import com.mahidx7.forkclient.client.ForkClientController;
import com.mahidx7.forkclient.client.permissions.FeaturePermissions;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @Unique
    @Inject(
        method = "submitArmWithItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            ordinal = 1
        )
    )
    private void forkClient$lowerShield(
        AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand,
        float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci
    ) {
        if (ForkClientController.INSTANCE.isModuleEnabled("low_shield")
            && FeaturePermissions.canUseRendering()
            && itemStack.getItem() instanceof ShieldItem
            && player.isBlocking()) {
            poseStack.translate(0.0F, -0.3F, 0.0F);
        }
    }
}
