package com.mahidx7.forkclient.client.modules.building;

import com.mahidx7.forkclient.client.ForkClientController;
import com.mahidx7.forkclient.client.permissions.FeaturePermissions;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

public final class ShapePreviewModule {
    public static final String MODULE_ID = "shape_preview";
    public static final String SETTING_RADIUS = "radius";
    public static final String SETTING_SHAPE_TYPE = "shape_type";

    private static final int DEFAULT_RADIUS = 5;
    private static final int MIN_RADIUS = 1;
    private static final int MAX_RADIUS = 32;
    private static final int LINE_SEGMENTS = 64;
    private static final int SHAPE_SPHERE = 0;
    private static final int SHAPE_CIRCLE = 1;
    private static final int SHAPE_CYLINDER = 2;
    private static final int SHAPE_DOME = 3;

    private static final int SHAPE_COLOR = 0x6600FFFF;
    private static float lineWidth = 0.02F;

    private ShapePreviewModule() {
    }

    public static boolean isEnabled() {
        return FeaturePermissions.canUseRendering()
                && ForkClientController.INSTANCE.isModuleEnabled(MODULE_ID);
    }

    public static int getRadius() {
        return Math.round(ForkClientController.INSTANCE.getModuleFloatSetting(MODULE_ID, SETTING_RADIUS, DEFAULT_RADIUS));
    }

    public static void setRadius(int value) {
        ForkClientController.INSTANCE.setModuleFloatSetting(MODULE_ID, SETTING_RADIUS, Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, value)));
    }

    public static int getShapeType() {
        return Math.round(ForkClientController.INSTANCE.getModuleFloatSetting(MODULE_ID, SETTING_SHAPE_TYPE, SHAPE_CIRCLE));
    }

    public static void setShapeType(int type) {
        ForkClientController.INSTANCE.setModuleFloatSetting(MODULE_ID, SETTING_SHAPE_TYPE, Math.max(0, Math.min(3, type)));
    }

    public static String getShapeTypeName() {
        return switch (getShapeType()) {
            case SHAPE_SPHERE -> "Sphere";
            case SHAPE_CIRCLE -> "Circle";
            case SHAPE_CYLINDER -> "Cylinder";
            case SHAPE_DOME -> "Dome";
            default -> "Circle";
        };
    }

    public static void render(Minecraft client) {
        if (!isEnabled()) return;

        LocalPlayer player = client.player;
        if (player == null) return;

        int radius = getRadius();
        int shapeType = getShapeType();
        Vec3 center = player.position();

        switch (shapeType) {
            case SHAPE_SPHERE -> renderSphere(center, radius);
            case SHAPE_CIRCLE -> renderCircle(center, radius);
            case SHAPE_CYLINDER -> renderCylinder(center, radius);
            case SHAPE_DOME -> renderDome(center, radius);
        }
    }

    private static void renderCircle(Vec3 center, int radius) {
        Vec3 prev = null;
        for (int i = 0; i <= LINE_SEGMENTS; i++) {
            double angle = 2.0 * Math.PI * i / LINE_SEGMENTS;
            double x = center.x + radius * Math.cos(angle);
            double z = center.z + radius * Math.sin(angle);
            Vec3 current = new Vec3(x, center.y, z);
            if (prev != null) {
                Gizmos.line(prev, current, SHAPE_COLOR, lineWidth);
            }
            prev = current;
        }
    }

    private static void renderSphere(Vec3 center, int radius) {
        renderCircle(center, radius);
        renderVerticalCircle(center, radius, 0);
        renderVerticalCircle(center, radius, 90);
    }

    private static void renderVerticalCircle(Vec3 center, int radius, int rotationDegrees) {
        double rotationRad = Math.toRadians(rotationDegrees);
        double cosR = Math.cos(rotationRad);
        double sinR = Math.sin(rotationRad);

        Vec3 prev = null;
        for (int i = 0; i <= LINE_SEGMENTS; i++) {
            double angle = 2.0 * Math.PI * i / LINE_SEGMENTS;
            double localX = radius * Math.cos(angle);
            double localY = radius * Math.sin(angle);
            double worldX = center.x + localX * cosR;
            double worldZ = center.z + localX * sinR;
            double worldY = center.y + localY;
            Vec3 current = new Vec3(worldX, worldY, worldZ);
            if (prev != null) {
                Gizmos.line(prev, current, SHAPE_COLOR, lineWidth);
            }
            prev = current;
        }
    }

    private static void renderCylinder(Vec3 center, int radius) {
        renderCircle(center, radius);
        renderCircle(new Vec3(center.x, center.y + radius, center.z), radius);

        for (int i = 0; i < 8; i++) {
            double angle = 2.0 * Math.PI * i / 8;
            double x = center.x + radius * Math.cos(angle);
            double z = center.z + radius * Math.sin(angle);
            Gizmos.line(
                    new Vec3(x, center.y, z),
                    new Vec3(x, center.y + radius, z),
                    SHAPE_COLOR, lineWidth
            );
        }
    }

    private static void renderDome(Vec3 center, int radius) {
        renderCircle(center, radius);
        renderVerticalCircle(center, radius, 0);
        renderVerticalCircle(center, radius, 90);

        for (int i = 0; i < 8; i++) {
            double angle = 2.0 * Math.PI * i / 8;
            double x = center.x + radius * Math.cos(angle);
            double z = center.z + radius * Math.sin(angle);
            Gizmos.line(
                    new Vec3(x, center.y, z),
                    new Vec3(center.x, center.y + radius, center.z),
                    SHAPE_COLOR, lineWidth
            );
        }
    }

    public static List<String> getLines() {
        return List.of(
                "Shape: " + getShapeTypeName(),
                "Radius: " + getRadius()
        );
    }
}
