package com.mahidx7.forkclient.mixin;

import com.mahidx7.forkclient.client.modules.AntiKnockbackModule;
import com.mahidx7.forkclient.client.permissions.FeaturePermissions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class VelocityMixin {
    @Unique
    @Inject(method = "handleSetEntityMotion", at = @At("HEAD"), cancellable = true)
    private void forkClient$onVelocityPacket(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        if (!FeaturePermissions.canUseMovement()) {
            return;
        }

        if (!AntiKnockbackModule.isEnabled()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }

        if (packet.id() != client.player.getId()) {
            return;
        }

        double originalX = packet.movement().x();
        double originalY = packet.movement().y();
        double originalZ = packet.movement().z();

        float horizontalMultiplier = AntiKnockbackModule.getHorizontalMultiplier();
        float verticalMultiplier = AntiKnockbackModule.getVerticalMultiplier();

        double reducedX = originalX * horizontalMultiplier;
        double reducedY = originalY * verticalMultiplier;
        double reducedZ = originalZ * horizontalMultiplier;

        client.player.setDeltaMovement(reducedX, reducedY, reducedZ);

        ci.cancel();
    }
}
