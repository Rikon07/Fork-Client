package com.mahidx7.forkclient.mixin;

import com.mahidx7.forkclient.client.ForkClientController;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {
    private static final DateTimeFormatter FORK_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @Unique
    private static boolean forkClient$timestamping;

    @Unique
    private Component forkClient$addTimestamp(Component message) {
        if (forkClient$timestamping) {
            return message;
        }

        if (!ForkClientController.INSTANCE.isModuleEnabled("chat_timestamps")) {
            return message;
        }

        String text = message.getString();
        if (text.startsWith("[") && text.length() > 6 && text.charAt(6) == ']') {
            return message;
        }

        String timestamp = "[" + LocalTime.now().format(FORK_TIME_FMT) + "] ";
        MutableComponent stamped = Component.literal(timestamp).withStyle(Style.EMPTY.withColor(0xFFAAAAAA));
        stamped.append(message.copy());
        return stamped;
    }

    @Unique
    @Inject(method = "addServerSystemMessage", at = @At("HEAD"), cancellable = true)
    private void forkClient$timestampServerMessage(Component message, CallbackInfo ci) {
        if (forkClient$timestamping) return;
        Component stamped = forkClient$addTimestamp(message);
        if (stamped == message) return;

        forkClient$timestamping = true;
        try {
            ChatComponent self = (ChatComponent) (Object) this;
            self.addServerSystemMessage(stamped);
        } finally {
            forkClient$timestamping = false;
        }
        ci.cancel();
    }

    @Unique
    @Inject(method = "addClientSystemMessage", at = @At("HEAD"), cancellable = true)
    private void forkClient$timestampClientMessage(Component message, CallbackInfo ci) {
        if (forkClient$timestamping) return;
        Component stamped = forkClient$addTimestamp(message);
        if (stamped == message) return;

        forkClient$timestamping = true;
        try {
            ChatComponent self = (ChatComponent) (Object) this;
            self.addClientSystemMessage(stamped);
        } finally {
            forkClient$timestamping = false;
        }
        ci.cancel();
    }

    @Unique
    @Inject(method = "addPlayerMessage", at = @At("HEAD"), cancellable = true)
    private void forkClient$timestampPlayerMessage(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        if (forkClient$timestamping) return;
        Component stamped = forkClient$addTimestamp(message);
        if (stamped == message) return;

        forkClient$timestamping = true;
        try {
            ChatComponent self = (ChatComponent) (Object) this;
            self.addPlayerMessage(stamped, signature, tag);
        } finally {
            forkClient$timestamping = false;
        }
        ci.cancel();
    }
}
