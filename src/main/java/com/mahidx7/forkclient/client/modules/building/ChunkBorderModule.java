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

public final class ChunkBorderModule {
    public static final String MODULE_ID = "chunk_border_viewer";

    private static final ChunkBorderCache cache = new ChunkBorderCache();

    private ChunkBorderModule() {
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
        int pz = (int) Math.floor(player.getZ());

        int playerChunkX = px >> 4;
        int playerChunkZ = pz >> 4;

        if (cache.level != client.level
                || cache.px != playerChunkX
                || cache.pz != playerChunkZ) {
            cache.level = client.level;
            cache.px = playerChunkX;
            cache.pz = playerChunkZ;
            cache.lines.clear();

            int viewDist = 4;
            int lineColor = 0x88FF3333;
            float lineWidth = 0.05F;
            int yTop = 320;
            int yBottom = -64;

            for (int cx = playerChunkX - viewDist; cx <= playerChunkX + viewDist; cx++) {
                for (int cz = playerChunkZ - viewDist; cz <= playerChunkZ + viewDist; cz++) {
                    int bx = cx << 4;
                    int bz = cz << 4;

                    cache.lines.add(new GizmoLine(new Vec3(bx, yBottom, bz), new Vec3(bx, yTop, bz), lineColor, lineWidth));
                    cache.lines.add(new GizmoLine(new Vec3(bx + 16, yBottom, bz), new Vec3(bx + 16, yTop, bz), lineColor, lineWidth));
                    cache.lines.add(new GizmoLine(new Vec3(bx, yBottom, bz + 16), new Vec3(bx, yTop, bz + 16), lineColor, lineWidth));
                    cache.lines.add(new GizmoLine(new Vec3(bx + 16, yBottom, bz + 16), new Vec3(bx + 16, yTop, bz + 16), lineColor, lineWidth));
                }
            }
        }

        for (GizmoLine line : cache.lines) {
            Gizmos.line(line.from, line.to, line.color, line.width);
        }
    }

    public record GizmoLine(Vec3 from, Vec3 to, int color, float width) {
    }

    private static final class ChunkBorderCache {
        Level level;
        int px;
        int pz;
        final List<GizmoLine> lines = new ArrayList<>();
    }
}
