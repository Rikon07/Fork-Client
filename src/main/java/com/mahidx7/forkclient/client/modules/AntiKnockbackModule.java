package com.mahidx7.forkclient.client.modules;

import com.mahidx7.forkclient.client.ForkClientController;
import com.mahidx7.forkclient.client.permissions.FeaturePermissions;

public final class AntiKnockbackModule {
    public static final String MODULE_ID = "anti_knockback";
    public static final String SETTING_HORIZONTAL = "horizontal";
    public static final String SETTING_VERTICAL = "vertical";

    private static final float DEFAULT_HORIZONTAL = 0.0F;
    private static final float DEFAULT_VERTICAL = 0.0F;

    private AntiKnockbackModule() {
    }

    public static boolean isEnabled() {
        return FeaturePermissions.canUseMovement()
                && ForkClientController.INSTANCE.isModuleEnabled(MODULE_ID);
    }

    public static float getHorizontalReduction() {
        return ForkClientController.INSTANCE.getModuleFloatSetting(MODULE_ID, SETTING_HORIZONTAL, DEFAULT_HORIZONTAL);
    }

    public static float getVerticalReduction() {
        return ForkClientController.INSTANCE.getModuleFloatSetting(MODULE_ID, SETTING_VERTICAL, DEFAULT_VERTICAL);
    }

    public static void setHorizontalReduction(float value) {
        ForkClientController.INSTANCE.setModuleFloatSetting(MODULE_ID, SETTING_HORIZONTAL, clamp(value, 0.0F, 1.0F));
    }

    public static void setVerticalReduction(float value) {
        ForkClientController.INSTANCE.setModuleFloatSetting(MODULE_ID, SETTING_VERTICAL, clamp(value, 0.0F, 1.0F));
    }

    public static float getHorizontalMultiplier() {
        return 1.0F - clamp(getHorizontalReduction(), 0.0F, 1.0F);
    }

    public static float getVerticalMultiplier() {
        return 1.0F - clamp(getVerticalReduction(), 0.0F, 1.0F);
    }

    private static float clamp(float value, float min, float max) {
        return Math.clamp(value, min, max);
    }
}
