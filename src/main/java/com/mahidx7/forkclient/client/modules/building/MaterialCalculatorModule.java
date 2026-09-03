package com.mahidx7.forkclient.client.modules.building;

import com.mahidx7.forkclient.client.ForkClientController;
import com.mahidx7.forkclient.client.permissions.FeaturePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

public final class MaterialCalculatorModule {
    public static final String MODULE_ID = "material_calculator";

    private static final int MAX_REGION_VOLUME = 500000;
    private static List<String> cachedResult = null;
    private static int cachedVolume = 0;

    private MaterialCalculatorModule() {
    }

    public static boolean isEnabled() {
        return FeaturePermissions.canUseRendering()
                && ForkClientController.INSTANCE.isModuleEnabled(MODULE_ID);
    }

    public static void calculate(Minecraft client) {
        if (client.level == null || client.player == null) {
            cachedResult = List.of("No world loaded");
            return;
        }

        if (!BlockMeasurementModule.isMeasureComplete()) {
            cachedResult = List.of("Set 2 points first", "(Use Block Measurement)");
            return;
        }

        int x1 = Math.min(BlockMeasurementModule.getMeasureX1(), BlockMeasurementModule.getMeasureX2());
        int y1 = Math.min(BlockMeasurementModule.getMeasureY1(), BlockMeasurementModule.getMeasureY2());
        int z1 = Math.min(BlockMeasurementModule.getMeasureZ1(), BlockMeasurementModule.getMeasureZ2());
        int x2 = Math.max(BlockMeasurementModule.getMeasureX1(), BlockMeasurementModule.getMeasureX2());
        int y2 = Math.max(BlockMeasurementModule.getMeasureY1(), BlockMeasurementModule.getMeasureY2());
        int z2 = Math.max(BlockMeasurementModule.getMeasureZ1(), BlockMeasurementModule.getMeasureZ2());

        int volume = (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
        if (volume > MAX_REGION_VOLUME) {
            cachedResult = List.of("Region too large", volume + " blocks", "Max: " + MAX_REGION_VOLUME);
            return;
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int airCount = 0;

        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    pos.set(x, y, z);
                    BlockState state = client.level.getBlockState(pos);
                    if (state.isAir()) {
                        airCount++;
                        continue;
                    }
                    Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    String name = key != null ? key.toString() : "unknown";
                    counts.merge(name, 1, Integer::sum);
                }
            }
        }

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());

        List<String> result = new ArrayList<>();
        int solidBlocks = volume - airCount;
        result.add("Total: " + solidBlocks + " blocks");
        int shown = Math.min(sorted.size(), 10);
        for (int i = 0; i < shown; i++) {
            Map.Entry<String, Integer> entry = sorted.get(i);
            result.add(formatBlockName(entry.getKey()) + ": " + entry.getValue());
        }
        if (sorted.size() > 10) {
            result.add("+" + (sorted.size() - 10) + " more types");
        }
        cachedResult = result;
        cachedVolume = solidBlocks;
    }

    public static List<String> getLines() {
        if (cachedResult != null) {
            return cachedResult;
        }
        return List.of("Material Calc", "Press calculate");
    }

    public static int getCachedVolume() {
        return cachedVolume;
    }

    private static String formatBlockName(String blockId) {
        String path = blockId;
        int slash = path.lastIndexOf('/');
        if (slash >= 0) path = path.substring(slash + 1);
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1));
        }
        return sb.toString();
    }
}
