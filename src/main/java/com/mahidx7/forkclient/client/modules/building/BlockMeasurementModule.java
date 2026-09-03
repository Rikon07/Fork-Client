package com.mahidx7.forkclient.client.modules.building;

import com.mahidx7.forkclient.client.ForkClientController;
import com.mahidx7.forkclient.client.permissions.FeaturePermissions;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class BlockMeasurementModule {
    public static final String MODULE_ID = "block_measurement";

    private static int measureX1, measureY1, measureZ1;
    private static int measureX2, measureY2, measureZ2;
    private static boolean measureFirstSet;
    private static boolean measureSecondSet;

    private BlockMeasurementModule() {
    }

    public static boolean isEnabled() {
        return FeaturePermissions.canUseRendering()
                && ForkClientController.INSTANCE.isModuleEnabled(MODULE_ID);
    }

    public static void onTick(Minecraft client) {
        if (!isEnabled() || client.player == null) return;

        var hitResult = client.hitResult;
        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            var blockHit = (BlockHitResult) hitResult;
            BlockPos pos = blockHit.getBlockPos();
            if (client.options.keyUse.isDown() && !client.options.keyAttack.isDown()) {
                if (!measureFirstSet) {
                    measureX1 = pos.getX();
                    measureY1 = pos.getY();
                    measureZ1 = pos.getZ();
                    measureFirstSet = true;
                    measureSecondSet = false;
                }
            }
            if (client.options.keyAttack.isDown() && !client.options.keyUse.isDown()) {
                if (measureFirstSet) {
                    measureX2 = pos.getX();
                    measureY2 = pos.getY();
                    measureZ2 = pos.getZ();
                    measureSecondSet = true;
                }
            }
        }
    }

    public static List<String> getLines() {
        if (!measureSecondSet) {
            return List.of("Measurement --", "Use: Attack + Use");
        }
        int dx = Math.abs(measureX2 - measureX1) + 1;
        int dy = Math.abs(measureY2 - measureY1) + 1;
        int dz = Math.abs(measureZ2 - measureZ1) + 1;
        int volume = dx * dy * dz;
        double dist = Math.sqrt(
                Math.pow(measureX2 - measureX1, 2) +
                Math.pow(measureY2 - measureY1, 2) +
                Math.pow(measureZ2 - measureZ1, 2)
        );
        return List.of(
                dx + " x " + dy + " x " + dz,
                "Vol " + volume,
                String.format(Locale.ROOT, "Dist %.1f", dist)
        );
    }

    public static int getMeasureX1() { return measureX1; }
    public static int getMeasureY1() { return measureY1; }
    public static int getMeasureZ1() { return measureZ1; }
    public static int getMeasureX2() { return measureX2; }
    public static int getMeasureY2() { return measureY2; }
    public static int getMeasureZ2() { return measureZ2; }
    public static boolean isMeasureComplete() { return measureSecondSet; }

    public static void clearMeasurement() {
        measureFirstSet = false;
        measureSecondSet = false;
    }
}
