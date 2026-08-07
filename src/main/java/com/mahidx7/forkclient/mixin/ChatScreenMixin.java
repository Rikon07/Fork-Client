package com.mahidx7.forkclient.mixin;

import com.mahidx7.forkclient.client.modules.MotionBlurPlusModule;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Unique
    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void forkClient$handleMotionBlurPlusCommand(String message, boolean allowOverwrite, CallbackInfo ci) {
        if (MotionBlurPlusModule.handleCommand(message)) {
            ci.cancel();
        }
    }
}
