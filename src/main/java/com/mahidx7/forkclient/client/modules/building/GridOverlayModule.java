package com.mahidx7.forkclient.client.modules.building;

import com.mahidx7.forkclient.client.ForkClientController;
import com.mahidx7.forkclient.client.permissions.FeaturePermissions;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class GridOverlayModule {
    public static final String MODULE_ID = "grid_overlay";

    private static final GridOverlayCache cache = new GridOverlayCache();

    private GridOverlayModule() {
    }

    public static boolean isEnabled() {
        return FeaturePermissions.canUseRendering()
                && ForkClientController.INSTANCE.isModuleEnabled(MODULE_ID);
    }

    public static void render(Minecraft client) {
        if (!isEnabled()) return;

        LocalPlayer player = client.player;
        if (player == null || client.level == null) return;

        int px = (int) Math.floor(player.getX());
        int py = (int) Math.floor(player.getY());
        int pz = (int) Math.floor(player.getZ());

        if (cache.level != client.level
                || cache.px != px
                || cache.py != py
                || cache.pz != pz) {
            cache.level = client.level;
            cache.px = px;
            cache.py = py;
            cache.pz = pz;
            cache.lines.clear();

            int range = 16;
            int gridColor = 0x44FFFF00;
            float lineHeight = 0.01F;

            for (int x = px - range; x <= px + range; x++) {
                cache.lines.add(new GizmoLine(new Vec3(x, py - 0.001, pz - range), new Vec3(x, py - 0.001, pz + range), gridColor, lineHeight));
            }

            for (int z = pz - range; z <= pz + range; z++) {
                cache.lines.add(new GizmoLine(new Vec3(px - range, py - 0.001, z), new Vec3(px + range, py - 0.001, z), gridColor, lineHeight));
            }

            int chunkColor = 0x55FF8800;
            int playerChunkX = px >> 4;
            int playerChunkZ = pz >> 4;
            for (int cx = playerChunkX - 2; cx <= playerChunkX + 2; cx++) {
                int blockX = cx << 4;
                cache.lines.add(new GizmoLine(new Vec3(blockX, py - 0.002, pz - range), new Vec3(blockX, py - 0.002, pz + range), chunkColor, lineHeight + 0.01F));
                cache.lines.add(new GizmoLine(new Vec3(blockX + 16, py - 0.002, pz - range), new Vec3(blockX + 16, py - 0.002, pz + range), chunkColor, lineHeight + 0.01F));
            }
            for (int cz = playerChunkZ - 2; cz <= playerChunkZ + 2; cz++) {
                int blockZ = cz << 4;
                cache.lines.add(new GizmoLine(new Vec3(px - range, py - 0.002, blockZ), new Vec3(px + range, py - 0.002, blockZ), chunkColor, lineHeight + 0.01F));
                cache.lines.add(new GizmoLine(new Vec3(px - range, py - 0.002, blockZ + 16), new Vec3(px + range, py - 0.002, blockZ + 16), chunkColor, lineHeight + 0.01F));
            }
        }

        for (GizmoLine line : cache.lines) {
            Gizmos.line(line.from, line.to, line.color, line.width);
        }
    }

    public record GizmoLine(Vec3 from, Vec3 to, int color, float width) {
    }

    private static final class GridOverlayCache {
        Level level;
        int px;
        int py;
        int pz;
        final List<GizmoLine> lines = new ArrayList<>();
    }
}
