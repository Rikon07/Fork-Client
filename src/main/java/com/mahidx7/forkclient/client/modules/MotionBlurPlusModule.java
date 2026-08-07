package com.mahidx7.forkclient.client.modules;

import com.mahidx7.forkclient.client.ForkClientController;
import com.mahidx7.forkclient.client.permissions.FeaturePermissions;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class MotionBlurPlusModule {
    public static final String MODULE_ID = "motion_blur_plus";
    public static final String SETTING_STRENGTH = "strength";

    public static final int DEFAULT_STRENGTH = 50;
    public static final int MAX_STRENGTH = 100;

    private static final String COMMAND_PREFIX = "/motionblurplus";
    private static final String KEYBIND_TRANSLATION = "key.fork-client.motion_blur_plus_settings";

    private static final int ACCENT_COLOR = 0xFF55D0FF;

    private static KeyMapping openSettingsKey;

    private MotionBlurPlusModule() {
    }

    @SuppressWarnings("deprecation")
    public static void initialize() {
        registerKeybind();
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new BlurAssetsReloader());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSettingsKey.consumeClick()) {
                openSettingsScreen(client);
            }
        });
    }

    private static void registerKeybind() {
        openSettingsKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(KEYBIND_TRANSLATION, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M,
                        ForkClientController.INSTANCE.getKeyCategory()));
    }

    public static void openSettingsScreen(Minecraft client) {
        if (client.gui.screen() instanceof MotionBlurOptionsScreen) {
            return;
        }
        client.gui.setScreen(new MotionBlurOptionsScreen(client.gui.screen()));
    }

    public static boolean isEnabled() {
        return FeaturePermissions.canUseRendering()
                && ForkClientController.INSTANCE.isModuleEnabled(MODULE_ID);
    }

    public static int getStrength() {
        return Math.round(ForkClientController.INSTANCE.getModuleFloatSetting(MODULE_ID, SETTING_STRENGTH, DEFAULT_STRENGTH));
    }

    public static void setStrength(int value) {
        ForkClientController.INSTANCE.setModuleFloatSetting(MODULE_ID, SETTING_STRENGTH, clamp(value));
    }

    public static void setEnabled(boolean enabled) {
        if (ForkClientController.INSTANCE.isModuleEnabled(MODULE_ID) != enabled) {
            ForkClientController.INSTANCE.toggleModule(MODULE_ID);
        }
    }

    public static void applySettings(boolean enabled, int strength) {
        setEnabled(enabled);
        setStrength(strength);
        MotionBlurPlusRenderer.resetHistory();
    }

    public static boolean handleCommand(String message) {
        String[] parts = message.split("\\s+");
        if (parts.length == 0 || !parts[0].equalsIgnoreCase(COMMAND_PREFIX)) {
            return false;
        }

        if (parts.length < 2) {
            showUsage();
            return true;
        }

        String command = parts[1].toLowerCase(Locale.ROOT);
        switch (command) {
            case "percentage" -> {
                if (parts.length < 3) {
                    feedback("Usage: /motionblurplus percentage <0-100>");
                    return true;
                }
                try {
                    applySettings(isEnabled(), Integer.parseInt(parts[2]));
                    feedback("Motion Blur Plus strength set to " + getStrength() + "%");
                } catch (NumberFormatException e) {
                    feedback("Invalid strength value \"" + parts[2] + "\" (expected 0-100)");
                }
                return true;
            }
            case "toggle" -> {
                if (parts.length < 3) {
                    feedback("Usage: /motionblurplus toggle <enable|disable>");
                    return true;
                }
                String state = parts[2];
                if ("enable".equalsIgnoreCase(state)) {
                    applySettings(true, getStrength());
                    feedback("Motion Blur Plus enabled");
                } else if ("disable".equalsIgnoreCase(state)) {
                    applySettings(false, getStrength());
                    feedback("Motion Blur Plus disabled");
                } else {
                    feedback("Use enable or disable");
                }
                return true;
            }
            case "status" -> {
                feedback(buildStatus());
                return true;
            }
            default -> showUsage();
        }
        return true;
    }

    private static void showUsage() {
        feedback("Motion Blur Plus commands:");
        feedback("/motionblurplus percentage <0-100>");
        feedback("/motionblurplus toggle <enable|disable>");
        feedback("/motionblurplus status");
    }

    private static String buildStatus() {
        return "Motion Blur Plus: " + (isEnabled() ? "enabled" : "disabled") + " | strength " + getStrength() + "%";
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(MAX_STRENGTH, value));
    }

    private static void feedback(String message) {
        ForkClientController.INSTANCE.addRecentAlert(message);
        ForkClientController.INSTANCE.pushNotification(message, ACCENT_COLOR, 3000L);
    }
}
