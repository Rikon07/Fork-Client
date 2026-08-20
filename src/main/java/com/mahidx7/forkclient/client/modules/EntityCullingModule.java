package com.mahidx7.forkclient.client.modules;

import com.mahidx7.forkclient.client.ForkClientController;
import com.mahidx7.forkclient.client.permissions.FeaturePermissions;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Entity Culling module – boosts performance by preventing the client from rendering
 * entities hidden behind solid blocks.  Uses asynchronous DDA ray-step path-tracing
 * across a cached thread pool; results are double-buffered so the current frame always
 * reads the previous frame's visibility data without stalling.
 */
public final class EntityCullingModule {

    public static final String MODULE_ID = "entity_culling";

    private static final double MAX_TRACE_DISTANCE = 128.0;
    private static final double MIN_CULL_DISTANCE = 1.5;

    private static final ExecutorService TRACE_POOL =
            Executors.newFixedThreadPool(
                    Math.max(2, Runtime.getRuntime().availableProcessors() - 1),
                    r -> {
                        Thread t = new Thread(r, "EntityCulling-Trace");
                        t.setDaemon(true);
                        t.setPriority(Thread.NORM_PRIORITY - 1);
                        return t;
                    });

    private static volatile Map<Long, Boolean> writeBuffer = new ConcurrentHashMap<>();
    private static volatile Map<Long, Boolean> readBuffer = Map.of();
    private static final Set<Long> skipPositions = new CopyOnWriteArraySet<>();

    private EntityCullingModule() {
    }

    public static boolean isEnabled() {
        return FeaturePermissions.canUseRendering()
                && ForkClientController.INSTANCE.isModuleEnabled(MODULE_ID);
    }

    /**
     * Called once per frame from {@code LevelRendererMixin} at the HEAD of
     * {@code submitEntities}.  Performs three steps:
     * <ol>
     *   <li>Swaps double buffers – previous frame's writes become this frame's reads.</li>
     *   <li>Populates the per-frame skip set from readBuffer results.</li>
     *   <li>Submits async ray-trace tasks for cold entries (not yet in readBuffer).</li>
     * </ol>
     */
    public static void update(List<EntityRenderState> states, Vec3 cameraPos) {
        if (!isEnabled()) {
            skipPositions.clear();
            return;
        }

        skipPositions.clear();

        Map<Long, Boolean> oldWrite = writeBuffer;
        writeBuffer = new ConcurrentHashMap<>();
        readBuffer = oldWrite;

        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        for (EntityRenderState state : states) {
            long key = positionKey(state);
            Boolean cached = readBuffer.get(key);
            if (cached != null) {
                if (!cached) {
                    skipPositions.add(key);
                }
            } else {
                double dx = state.x - cameraPos.x;
                double dy = state.y - cameraPos.y;
                double dz = state.z - cameraPos.z;
                double distSq = dx * dx + dy * dy + dz * dz;

                if (distSq < MIN_CULL_DISTANCE * MIN_CULL_DISTANCE
                        || distSq > MAX_TRACE_DISTANCE * MAX_TRACE_DISTANCE) {
                    writeBuffer.put(key, false);
                } else {
                    Vec3 from = cameraPos;
                    Vec3 to = new Vec3(state.x, state.y + state.eyeHeight * 0.5, state.z);
                    double dist = Math.sqrt(distSq);
                    TRACE_POOL.submit(() -> {
                        boolean occluded = isOccluded(level, from, to, dist);
                        writeBuffer.put(key, occluded);
                    });
                }
            }
        }
    }

    public static boolean shouldSkip(EntityRenderState state) {
        return skipPositions.contains(positionKey(state));
    }

    public static void shutdown() {
        TRACE_POOL.shutdownNow();
    }

    private static long positionKey(EntityRenderState state) {
        int x = (int) (state.x * 100.0);
        int y = (int) (state.y * 100.0);
        int z = (int) (state.z * 100.0);
        long h = x * 73856093L ^ y * 19349669L ^ z * 83492791L;
        return h != 0 ? h : 1L;
    }

    // ── DDA ray-step occlusion ─────────────────────────────────────────────

    private static boolean isOccluded(Level level, Vec3 from, Vec3 to, double dist) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;

        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6) {
            return false;
        }

        double invLen = 1.0 / len;
        double nx = dx * invLen;
        double ny = dy * invLen;
        double nz = dz * invLen;

        int bx = (int) Math.floor(from.x);
        int by = (int) Math.floor(from.y);
        int bz = (int) Math.floor(from.z);

        int endX = (int) Math.floor(to.x);
        int endY = (int) Math.floor(to.y);
        int endZ = (int) Math.floor(to.z);

        int stepX = nx > 0 ? 1 : (nx < 0 ? -1 : 0);
        int stepY = ny > 0 ? 1 : (ny < 0 ? -1 : 0);
        int stepZ = nz > 0 ? 1 : (nz < 0 ? -1 : 0);

        double tMaxX = nx != 0 ? ((nx > 0 ? (bx + 1) : bx) - from.x) / nx : Double.POSITIVE_INFINITY;
        double tMaxY = ny != 0 ? ((ny > 0 ? (by + 1) : by) - from.y) / ny : Double.POSITIVE_INFINITY;
        double tMaxZ = nz != 0 ? ((nz > 0 ? (bz + 1) : bz) - from.z) / nz : Double.POSITIVE_INFINITY;

        double tDeltaX = nx != 0 ? Math.abs(1.0 / nx) : Double.POSITIVE_INFINITY;
        double tDeltaY = ny != 0 ? Math.abs(1.0 / ny) : Double.POSITIVE_INFINITY;
        double tDeltaZ = nz != 0 ? Math.abs(1.0 / nz) : Double.POSITIVE_INFINITY;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double travelled = 0.0;

        while (travelled < dist) {
            pos.set(bx, by, bz);

            if (isOccludingBlock(level, pos)) {
                return true;
            }

            if (bx == endX && by == endY && bz == endZ) {
                break;
            }

            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    travelled = tMaxX;
                    bx += stepX;
                    tMaxX += tDeltaX;
                } else {
                    travelled = tMaxZ;
                    bz += stepZ;
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    travelled = tMaxY;
                    by += stepY;
                    tMaxY += tDeltaY;
                } else {
                    travelled = tMaxZ;
                    bz += stepZ;
                    tMaxZ += tDeltaZ;
                }
            }
        }

        return false;
    }

    /**
     * Uses the vanilla {@code isSolidRender()} predicate – the same check MC
     * uses internally for face-culling during chunk meshing.  Returns true
     * for fully opaque, full-cube blocks like stone, deepslate, and dirt;
     * false for air, glass, water, leaves, slabs, etc.
     */
    private static boolean isOccludingBlock(Level level, BlockPos pos) {
        try {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                return false;
            }
            return state.isSolidRender();
        } catch (Exception e) {
            return false;
        }
    }
}
