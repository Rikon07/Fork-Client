package com.mahidx7.forkclient.client.modules.building;

import com.mahidx7.forkclient.client.ForkClientController;
import com.mahidx7.forkclient.client.permissions.FeaturePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public final class BlockPaletteModule {
    public static final String MODULE_ID = "block_palette_viewer";
    public static final String SETTING_MAX_ROWS = "max_rows";

    private static final int DEFAULT_MAX_ROWS = 8;

    private static final List<PaletteEntry> palette = new ArrayList<>();
    private static final Map<String, String> colorGroups = new LinkedHashMap<>();

    static {
        colorGroups.put("stone", "Grey");
        colorGroups.put("wood", "Brown");
        colorGroups.put("wool", "Colorful");
        colorGroups.put("concrete", "Colorful");
        colorGroups.put("terracotta", "Earth");
        colorGroups.put("glass", "Transparent");
        colorGroups.put("brick", "Red");
        colorGroups.put("sand", "Yellow");
        colorGroups.put("dirt", "Brown");
        colorGroups.put("grass", "Green");
        colorGroups.put("leaves", "Green");
        colorGroups.put("plank", "Brown");
    }

    private BlockPaletteModule() {
    }

    public static boolean isEnabled() {
        return FeaturePermissions.canUseRendering()
                && ForkClientController.INSTANCE.isModuleEnabled(MODULE_ID);
    }

    public static int getMaxRows() {
        return Math.round(ForkClientController.INSTANCE.getModuleFloatSetting(MODULE_ID, SETTING_MAX_ROWS, DEFAULT_MAX_ROWS));
    }

    public static void setMaxRows(int value) {
        ForkClientController.INSTANCE.setModuleFloatSetting(MODULE_ID, SETTING_MAX_ROWS, Math.max(1, Math.min(16, value)));
    }

    public static void addBlock(String blockId) {
        if (getBlockIndex(blockId) < 0) {
            String group = categorizeBlock(blockId);
            palette.add(new PaletteEntry(blockId, group, false));
            ForkClientController.INSTANCE.saveConfig();
        }
    }

    public static void removeBlock(String blockId) {
        int index = getBlockIndex(blockId);
        if (index >= 0) {
            palette.remove(index);
            ForkClientController.INSTANCE.saveConfig();
        }
    }

    public static void toggleFavorite(String blockId) {
        int index = getBlockIndex(blockId);
        if (index >= 0) {
            PaletteEntry entry = palette.get(index);
            palette.set(index, new PaletteEntry(entry.blockId, entry.group, !entry.favorite));
            ForkClientController.INSTANCE.saveConfig();
        }
    }

    public static void addFromHeldItem(Minecraft client) {
        if (client.player == null) return;
        ItemStack held = client.player.getMainHandItem();
        if (held.isEmpty() || !(held.getItem() instanceof BlockItem blockItem)) return;
        Identifier key = BuiltInRegistries.ITEM.getKey(blockItem);
        if (key != null) {
            addBlock(key.toString());
        }
    }

    public static List<PaletteEntry> getPalette() {
        return List.copyOf(palette);
    }

    public static List<String> getLines(Minecraft client) {
        int maxRows = getMaxRows();
        if (palette.isEmpty()) {
            return List.of("Palette Empty", "Hold + use hotkey");
        }

        List<String> lines = new ArrayList<>();
        int shown = Math.min(palette.size(), maxRows);
        for (int i = 0; i < shown; i++) {
            PaletteEntry entry = palette.get(i);
            String fav = entry.favorite ? "*" : "";
            lines.add(fav + formatBlockName(entry.blockId));
        }
        if (palette.size() > maxRows) {
            lines.add("+" + (palette.size() - maxRows) + " more");
        }
        return lines;
    }

    public static void loadPalette(List<String[]> data) {
        palette.clear();
        if (data == null) return;
        for (String[] entry : data) {
            if (entry.length >= 2) {
                palette.add(new PaletteEntry(entry[0], entry[1], entry.length > 2 && Boolean.parseBoolean(entry[2])));
            }
        }
    }

    public static List<String[]> savePalette() {
        List<String[]> data = new ArrayList<>();
        for (PaletteEntry entry : palette) {
            data.add(new String[]{entry.blockId, entry.group, Boolean.toString(entry.favorite)});
        }
        return data;
    }

    private static int getBlockIndex(String blockId) {
        for (int i = 0; i < palette.size(); i++) {
            if (palette.get(i).blockId.equals(blockId)) return i;
        }
        return -1;
    }

    private static String categorizeBlock(String blockId) {
        String lower = blockId.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : colorGroups.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "Other";
    }

    private static String formatBlockName(String blockId) {
        String path = blockId;
        int slash = path.lastIndexOf('/');
        if (slash >= 0) path = path.substring(slash + 1);
        int colon = path.lastIndexOf(':');
        if (colon >= 0) path = path.substring(colon + 1);
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1));
        }
        return sb.toString();
    }

    public record PaletteEntry(String blockId, String group, boolean favorite) {
    }
}
