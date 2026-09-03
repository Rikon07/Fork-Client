package com.mahidx7.forkclient.client.modules.building;

import com.mahidx7.forkclient.client.ForkClientController;
import com.mahidx7.forkclient.client.permissions.FeaturePermissions;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

public final class BlueprintPreviewModule {
    public static final String MODULE_ID = "blueprint_preview";
    public static final String SETTING_WIDTH = "width";
    public static final String SETTING_HEIGHT = "height";
    public static final String SETTING_DEPTH = "depth";

    private static final int DEFAULT_SIZE = 10;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 64;

    private static final int BLUEPRINT_COLOR = 0x4400CCFF;
    private static final int BLUEPRINT_COLOR_EDGE = 0x6600AAFF;
    private static float lineWidth = 0.03F;
    private static float edgeLineWidth = 0.05F;

    private BlueprintPreviewModule() {
    }

    public static boolean isEnabled() {
        return FeaturePermissions.canUseRendering()
                && ForkClientController.INSTANCE.isModuleEnabled(MODULE_ID);
    }

    public static int getWidth() {
        return Math.round(ForkClientController.INSTANCE.getModuleFloatSetting(MODULE_ID, SETTING_WIDTH, DEFAULT_SIZE));
    }

    public static void setWidth(int value) {
        ForkClientController.INSTANCE.setModuleFloatSetting(MODULE_ID, SETTING_WIDTH, Math.max(MIN_SIZE, Math.min(MAX_SIZE, value)));
    }

    public static int getHeight() {
        return Math.round(ForkClientController.INSTANCE.getModuleFloatSetting(MODULE_ID, SETTING_HEIGHT, DEFAULT_SIZE));
    }

    public static void setHeight(int value) {
        ForkClientController.INSTANCE.setModuleFloatSetting(MODULE_ID, SETTING_HEIGHT, Math.max(MIN_SIZE, Math.min(MAX_SIZE, value)));
    }

    public static int getDepth() {
        return Math.round(ForkClientController.INSTANCE.getModuleFloatSetting(MODULE_ID, SETTING_DEPTH, DEFAULT_SIZE));
    }

    public static void setDepth(int value) {
        ForkClientController.INSTANCE.setModuleFloatSetting(MODULE_ID, SETTING_DEPTH, Math.max(MIN_SIZE, Math.min(MAX_SIZE, value)));
    }

    public static void render(Minecraft client) {
        if (!isEnabled()) return;

        LocalPlayer player = client.player;
        if (player == null) return;

        int w = getWidth();
        int h = getHeight();
        int d = getDepth();

        int bx = (int) Math.floor(player.getX());
        int by = (int) Math.floor(player.getY());
        int bz = (int) Math.floor(player.getZ());

        Vec3 origin = new Vec3(bx, by, bz);

        Vec3[] corners = {
                origin,
                origin.add(w, 0, 0),
                origin.add(w, 0, d),
                origin.add(0, 0, d),
                origin.add(0, h, 0),
                origin.add(w, h, 0),
                origin.add(w, h, d),
                origin.add(0, h, d)
        };

        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };

        for (int[] edge : edges) {
            Gizmos.line(corners[edge[0]], corners[edge[1]], BLUEPRINT_COLOR_EDGE, edgeLineWidth);
        }

        int gridStep = Math.max(1, Math.min(4, w / 4));
        for (int x = gridStep; x < w; x += gridStep) {
            Vec3 bottom = origin.add(x, 0, 0);
            Vec3 top = origin.add(x, 0, d);
            Gizmos.line(
                    new Vec3(bottom.x, bottom.y, bottom.z),
                    new Vec3(bottom.x, bottom.y + h, bottom.z),
                    BLUEPRINT_COLOR, lineWidth
            );
            Gizmos.line(
                    new Vec3(top.x, top.y, top.z),
                    new Vec3(top.x, top.y + h, top.z),
                    BLUEPRINT_COLOR, lineWidth
            );
        }

        gridStep = Math.max(1, Math.min(4, d / 4));
        for (int z = gridStep; z < d; z += gridStep) {
            Vec3 bottom = origin.add(0, 0, z);
            Vec3 top = origin.add(w, 0, z);
            Gizmos.line(
                    new Vec3(bottom.x, bottom.y, bottom.z),
                    new Vec3(bottom.x, bottom.y + h, bottom.z),
                    BLUEPRINT_COLOR, lineWidth
            );
            Gizmos.line(
                    new Vec3(top.x, top.y, top.z),
                    new Vec3(top.x, top.y + h, top.z),
                    BLUEPRINT_COLOR, lineWidth
            );
        }
    }

    public static List<String> getLines() {
        return List.of(
                getWidth() + "w x " + getHeight() + "h x " + getDepth() + "d",
                "Vol " + (getWidth() * getHeight() * getDepth())
        );
    }
}
