package com.mahidx7.forkclient.optimization.region;

import com.mahidx7.forkclient.optimization.chunk.TaskOutput;
import com.mahidx7.forkclient.optimization.storage.SectionStorage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the lifecycle of render regions, handles region grouping, staging buffers,
 * upload routing, and cached batch invalidation. Groups 8x4x8 render sections into
 * regions for batched GPU uploads and draw calls.
 */
public final class RenderRegionManager {

    private final Map<Long, RenderRegion> regions = new HashMap<>();

    public RenderRegionManager() {
    }

    /**
     * Uploads completed build results to their respective regions.
     * Groups uploads by region and invalidates cached batches only where
     * geometry actually changed.
     */
    public void uploadResults(List<TaskOutput> outputs) {
        Map<Long, List<TaskOutput>> byRegion = new HashMap<>();

        for (TaskOutput output : outputs) {
            SectionStorage.RenderSection section = output.section();
            long regionKey = packRegionKey(section.sectionX, section.sectionY, section.sectionZ);
            byRegion.computeIfAbsent(regionKey, k -> new ArrayList<>()).add(output);
        }

        for (Map.Entry<Long, List<TaskOutput>> entry : byRegion.entrySet()) {
            RenderRegion region = getOrCreateRegion(entry.getKey());
            for (TaskOutput output : entry.getValue()) {
                SectionStorage.RenderSection section = output.section();
                region.uploadSectionGeometry(
                        section.sectionX, section.sectionY, section.sectionZ,
                        output.vertexData(), output.indexData(),
                        output.vertexCount(), output.indexCount()
                );

                section.setFlag(SectionStorage.RenderSection.FLAG_IS_BUILT, true);
                section.setFlag(SectionStorage.RenderSection.FLAG_BLOCK_GEOMETRY, output.hasBlockGeometry());
                section.setFlag(SectionStorage.RenderSection.FLAG_ANIMATED_SPRITES, output.hasAnimatedSprites());
                section.setNeighborMask(output.neighborMask());
                section.setLastBuildTime(System.nanoTime());
                section.setDirty(false);
            }
        }
    }

    /**
     * Returns the region containing the given section.
     */
    public RenderRegion getRegion(int sectionX, int sectionY, int sectionZ) {
        long key = packRegionKey(sectionX, sectionY, sectionZ);
        return regions.get(key);
    }

    /**
     * Returns the region key for the given section coordinates.
     */
    public static long packRegionKey(int sectionX, int sectionY, int sectionZ) {
        int rx = Math.floorDiv(sectionX, RenderRegion.REGION_SECTIONS_X);
        int ry = Math.floorDiv(sectionY, RenderRegion.REGION_SECTIONS_Y);
        int rz = Math.floorDiv(sectionZ, RenderRegion.REGION_SECTIONS_Z);
        return ((long) rx & 0xFFFF) | (((long) ry & 0xFFFF) << 16) | (((long) rz & 0xFFFF) << 32);
    }

    /**
     * Returns all regions that contain visible sections.
     */
    public List<RenderRegion> getRegionsForSections(List<SectionStorage.RenderSection> sections) {
        Map<Long, RenderRegion> regionMap = new HashMap<>();
        for (SectionStorage.RenderSection section : sections) {
            long key = packRegionKey(section.sectionX, section.sectionY, section.sectionZ);
            RenderRegion region = regions.get(key);
            if (region != null) {
                regionMap.put(key, region);
            }
        }
        return new ArrayList<>(regionMap.values());
    }

    /**
     * Returns all active regions.
     */
    public Iterable<RenderRegion> allRegions() {
        return regions.values();
    }

    public int getRegionCount() {
        return regions.size();
    }

    private RenderRegion getOrCreateRegion(long regionKey) {
        return regions.computeIfAbsent(regionKey, k -> {
            int rx = (int) (k & 0xFFFF);
            int ry = (int) ((k >> 16) & 0xFFFF);
            int rz = (int) ((k >> 32) & 0xFFFF);
            return new RenderRegion(rx, ry, rz);
        });
    }

    public void clear() {
        for (RenderRegion region : regions.values()) {
            region.clear();
        }
        regions.clear();
    }

    /**
     * Removes regions that are no longer needed (all sections removed).
     */
    public void cleanup() {
        List<Long> toRemove = new ArrayList<>();
        for (Map.Entry<Long, RenderRegion> entry : regions.entrySet()) {
            RenderRegion region = entry.getValue();
            boolean hasGeometry = false;
            for (RenderRegion.DrawCommand cmd : region.buildDrawCommands()) {
                if (cmd.vertexCount() > 0) {
                    hasGeometry = true;
                    break;
                }
            }
            if (!hasGeometry) {
                toRemove.add(entry.getKey());
            }
        }
        for (Long key : toRemove) {
            RenderRegion region = regions.remove(key);
            if (region != null) {
                region.clear();
            }
        }
    }
}
