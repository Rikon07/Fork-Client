package com.mahidx7.forkclient.client.permissions;

import com.mahidx7.forkclient.network.MovementPermissionsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Set;

/**
 * Central permission manager for all Fork Client feature categories.
 *
 * <p>Permissions are granted by the server via {@link MovementPermissionsPayload}
 * and are reset on each disconnect. In single-player all features are permitted.
 *
 * <p>Thread safety: all state is guarded by the client thread (Minecraft.execute).
 */
public final class FeaturePermissions {

    private static final Logger LOGGER = LoggerFactory.getLogger("fork-client-permissions");

    public static final String TRANSLATION_KEY_MOVEMENT_DENIED = "message.fork-client.movement_denied";
    public static final String TRANSLATION_KEY_RENDERING_DENIED = "message.fork-client.rendering_denied";
    public static final String TRANSLATION_KEY_UTILITY_DENIED = "message.fork-client.utility_denied";

    private static final Set<PermissionFlag> grantedPermissions = EnumSet.noneOf(PermissionFlag.class);

    private static boolean receivedHandshakeResponse;

    private FeaturePermissions() {
    }

    public static boolean canUseMovement() {
        if (isSinglePlayer()) {
            return true;
        }
        if (!receivedHandshakeResponse) {
            return false;
        }
        return grantedPermissions.contains(PermissionFlag.MOVEMENT);
    }

    public static boolean canUseRendering() {
        if (isSinglePlayer()) {
            return true;
        }
        if (!receivedHandshakeResponse) {
            return false;
        }
        return grantedPermissions.contains(PermissionFlag.RENDERING);
    }

    public static boolean canUseUtilities() {
        if (isSinglePlayer()) {
            return true;
        }
        if (!receivedHandshakeResponse) {
            return false;
        }
        return grantedPermissions.contains(PermissionFlag.UTILITY);
    }

    public static boolean canUseAll() {
        if (isSinglePlayer()) {
            return true;
        }
        if (!receivedHandshakeResponse) {
            return false;
        }
        return grantedPermissions.containsAll(EnumSet.allOf(PermissionFlag.class));
    }

    public static boolean hasReceivedHandshakeResponse() {
        return receivedHandshakeResponse;
    }

    public static Component getDeniedMessage(PermissionFlag flag) {
        return switch (flag) {
            case MOVEMENT -> Component.translatable(TRANSLATION_KEY_MOVEMENT_DENIED);
            case RENDERING -> Component.translatable(TRANSLATION_KEY_RENDERING_DENIED);
            case UTILITY -> Component.translatable(TRANSLATION_KEY_UTILITY_DENIED);
        };
    }

    public static void applyPermissions(MovementPermissionsPayload payload) {
        grantedPermissions.clear();
        if (payload.movementAllowed()) {
            grantedPermissions.add(PermissionFlag.MOVEMENT);
        }
        if (payload.renderingAllowed()) {
            grantedPermissions.add(PermissionFlag.RENDERING);
        }
        if (payload.utilityAllowed()) {
            grantedPermissions.add(PermissionFlag.UTILITY);
        }
        receivedHandshakeResponse = true;
        LOGGER.debug("Permissions updated: {}", grantedPermissions);
    }

    public static void reset() {
        grantedPermissions.clear();
        receivedHandshakeResponse = false;
        LOGGER.debug("Permissions reset to defaults.");
    }

    private static boolean isSinglePlayer() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return false;
        }
        try {
            return client.hasSingleplayerServer();
        } catch (Exception e) {
            return false;
        }
    }

    public enum PermissionFlag {
        MOVEMENT,
        RENDERING,
        UTILITY
    }
}
