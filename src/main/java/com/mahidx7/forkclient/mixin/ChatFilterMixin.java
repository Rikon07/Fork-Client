package com.mahidx7.forkclient.mixin;

import com.mahidx7.forkclient.client.ForkClientController;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.chat.ChatListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatListener.class)
public class ChatFilterMixin {
    @Unique
    @Inject(method = "handlePlayerChatMessage", at = @At("HEAD"), cancellable = true)
    private void forkClient$filterPlayerChat(PlayerChatMessage message, GameProfile sender, ChatType.Bound chatType, CallbackInfo ci) {
        if (!ForkClientController.INSTANCE.isModuleEnabled("chat_filters")) {
            return;
        }
        String filterQuery = ForkClientController.INSTANCE.getModuleStringSetting("chat_filters", "filter_query", "").toLowerCase();
        if (filterQuery.isEmpty()) {
            return;
        }
        String content = message.signedContent().toLowerCase();
        if (content.contains(filterQuery)) {
            ci.cancel();
        }
    }

    @Unique
    @Inject(method = "handleSystemMessage", at = @At("HEAD"), cancellable = true)
    private void forkClient$filterSystemChat(Component message, boolean overlay, CallbackInfo ci) {
        if (!ForkClientController.INSTANCE.isModuleEnabled("chat_filters")) {
            return;
        }
        String filterQuery = ForkClientController.INSTANCE.getModuleStringSetting("chat_filters", "filter_query", "").toLowerCase();
        if (filterQuery.isEmpty()) {
            return;
        }
        String content = message.getString().toLowerCase();
        if (content.contains(filterQuery)) {
            ci.cancel();
        }
    }

    @Unique
    @Inject(method = "handleDisguisedChatMessage", at = @At("HEAD"), cancellable = true)
    private void forkClient$filterDisguisedChat(Component message, ChatType.Bound chatType, CallbackInfo ci) {
        if (!ForkClientController.INSTANCE.isModuleEnabled("chat_filters")) {
            return;
        }
        String filterQuery = ForkClientController.INSTANCE.getModuleStringSetting("chat_filters", "filter_query", "").toLowerCase();
        if (filterQuery.isEmpty()) {
            return;
        }
        String content = message.getString().toLowerCase();
        if (content.contains(filterQuery)) {
            ci.cancel();
        }
    }
}
