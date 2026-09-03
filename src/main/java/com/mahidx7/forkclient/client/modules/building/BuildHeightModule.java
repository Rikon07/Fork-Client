package com.mahidx7.forkclient.client.modules.building;

import com.mahidx7.forkclient.client.ForkClientController;
import com.mahidx7.forkclient.client.permissions.FeaturePermissions;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;

public final class BuildHeightModule {
    public static final String MODULE_ID = "build_height_indicator";

    private BuildHeightModule() {
    }

    public static boolean isEnabled() {
        return FeaturePermissions.canUseRendering()
                && ForkClientController.INSTANCE.isModuleEnabled(MODULE_ID);
    }

    public static List<String> getLines(LocalPlayer player) {
        if (player == null) {
            return List.of("Y --", "Limit --", "Left --");
        }
        int y = (int) Math.floor(player.getY());
        int buildMax = 320;
        int buildMin = -64;
        int remaining = buildMax - y;
        return List.of(
                "Y " + y,
                "Limit " + buildMax + " / " + buildMin,
                "Left " + remaining
        );
    }
}
