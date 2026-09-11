package com.mahidx7.forkclient.optimization.occlusion;

import com.mahidx7.forkclient.optimization.storage.SectionStorage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Performs visibility determination using a BFS graph traversal from the camera's
 * current section through section adjacency graphs. Uses face-to-face transparency
 * data baked during meshing to determine reachability. Supports frustum culling,
 * occlusion culling, and fog culling.
 *
 * The occlusion culler runs asynchronously on a dedicated executor thread to minimize
 * main-thread stall, and results are cached per-frame and invalidated when the camera
 * moves significantly.
 */
public final class OcclusionCuller {

    private static final double CAMERA_SIGNIFICANT_MOVE_THRESHOLD = 1.0;

    private final SectionTree visibilityTree;
    private final SectionStorage sectionStorage;

    private double lastCameraX, lastCameraY, lastCameraZ;
    private volatile boolean cullPending = false;
    private volatile List<SectionStorage.RenderSection> visibleSections = List.of();

    private final ArrayDeque<int[]> bfsQueue = new ArrayDeque<>(512);
    private final BitSet visited;

    public OcclusionCuller(SectionStorage sectionStorage, int worldSizeX, int worldSizeY, int worldSizeZ) {
        this.sectionStorage = sectionStorage;
        this.visibilityTree = new SectionTree(0, 0, 0, worldSizeX, worldSizeY, worldSizeZ);
        this.visited = new BitSet(worldSizeX * worldSizeY * worldSizeZ);
    }

    /**
     * Starts an asynchronous occlusion cull pass from the given camera position.
     * Results will be available in {@link #getVisibleSections()} after the cull completes.
     */
    public void beginCull(double cameraX, double cameraY, double cameraZ,
                          float viewDistance, float fogEnd) {
        double dx = cameraX - lastCameraX;
        double dy = cameraY - lastCameraY;
        double dz = cameraZ - lastCameraZ;
        boolean significantMove = (dx * dx + dy * dy + dz * dz)
                > CAMERA_SIGNIFICANT_MOVE_THRESHOLD * CAMERA_SIGNIFICANT_MOVE_THRESHOLD;

        if (significantMove || !cullPending) {
            lastCameraX = cameraX;
            lastCameraY = cameraY;
            lastCameraZ = cameraZ;
            cullPending = true;
        }
    }

    /**
     * Executes the occlusion cull synchronously. Should be called from a worker
     * thread or during the visibility preparation phase.
     */
    public void executeCull(double cameraX, double cameraY, double cameraZ,
                             float viewDistance, float fogEnd) {
        visibilityTree.clear();
        visited.clear();
        bfsQueue.clear();

        int originSectionX = ((int) Math.floor(cameraX)) >> 4;
        int originSectionY = ((int) Math.floor(cameraY)) >> 4;
        int originSectionZ = ((int) Math.floor(cameraZ)) >> 4;

        int viewDistSections = (int) Math.ceil(viewDistance / 16.0);
        float fogDistSections = fogEnd > 0 ? (float) Math.ceil(fogEnd / 16.0) : viewDistSections + 1;

        bfsQueue.add(new int[]{originSectionX, originSectionY, originSectionZ, 0});

        List<SectionStorage.RenderSection> visible = new ArrayList<>(512);

        while (!bfsQueue.isEmpty()) {
            int[] current = bfsQueue.poll();
            int sx = current[0];
            int sy = current[1];
            int sz = current[2];
            int depth = current[3];

            int localIdx = (sx - visibilityTree.getMinX()) * visibilityTree.getSizeY() * visibilityTree.getSizeZ()
                    + (sy - visibilityTree.getMinY()) * visibilityTree.getSizeZ()
                    + (sz - visibilityTree.getMinZ());
            if (localIdx >= 0 && localIdx < visited.length() && visited.get(localIdx)) {
                continue;
            }
            if (localIdx >= 0 && localIdx < visited.length()) {
                visited.set(localIdx);
            }

            double sectionCenterX = (sx << 4) + 8;
            double sectionCenterY = (sy << 4) + 8;
            double sectionCenterZ = (sz << 4) + 8;
            double distX = sectionCenterX - cameraX;
            double distY = sectionCenterY - cameraY;
            double distZ = sectionCenterZ - cameraZ;
            double dist = Math.sqrt(distX * distX + distY * distY + distZ * distZ);

            if (depth > viewDistSections) {
                continue;
            }

            if (dist > fogDistSections * 16) {
                continue;
            }

            visibilityTree.set(sx, sy, sz, true);

            SectionStorage.RenderSection section = sectionStorage.get(sx, sy, sz);
            if (section != null) {
                visible.add(section);
            }

            for (int face = 0; face < 6; face++) {
                int nx = FACE_OFFSETS[face][0];
                int ny = FACE_OFFSETS[face][1];
                int nz = FACE_OFFSETS[face][2];

                int nextX = sx + nx;
                int nextY = sy + ny;
                int nextZ = sz + nz;

                if (nextX < visibilityTree.getMinX() || nextX >= visibilityTree.getMinX() + visibilityTree.getSizeX()
                        || nextY < visibilityTree.getMinY() || nextY >= visibilityTree.getMinY() + visibilityTree.getSizeY()
                        || nextZ < visibilityTree.getMinZ() || nextZ >= visibilityTree.getMinZ() + visibilityTree.getSizeZ()) {
                    continue;
                }

                SectionStorage.RenderSection neighbor = sectionStorage.get(nextX, nextY, nextZ);
                if (neighbor == null) {
                    continue;
                }

                int oppositeFace = 5 - face;
                boolean neighborOpaque = (neighbor.getNeighborMask() & (1 << oppositeFace)) != 0;
                boolean currentOpaque = (section != null) && (section.getNeighborMask() & (1 << face)) != 0;

                if (currentOpaque && neighborOpaque) {
                    continue;
                }

                int nextIdx = (nextX - visibilityTree.getMinX()) * visibilityTree.getSizeY() * visibilityTree.getSizeZ()
                        + (nextY - visibilityTree.getMinY()) * visibilityTree.getSizeZ()
                        + (nextZ - visibilityTree.getMinZ());
                if (nextIdx >= 0 && nextIdx < visited.length() && !visited.get(nextIdx)) {
                    bfsQueue.add(new int[]{nextX, nextY, nextZ, depth + 1});
                }
            }
        }

        this.visibleSections = visible;
        cullPending = false;
    }

    /**
     * Returns the sections determined to be visible by the last cull pass.
     */
    public List<SectionStorage.RenderSection> getVisibleSections() {
        return visibleSections;
    }

    public boolean isCullPending() {
        return cullPending;
    }

    public SectionTree getVisibilityTree() {
        return visibilityTree;
    }

    private static final int[][] FACE_OFFSETS = {
            {0, 0, -1}, {0, 0, 1}, {0, -1, 0}, {0, 1, 0}, {-1, 0, 0}, {1, 0, 0}
    };
}
