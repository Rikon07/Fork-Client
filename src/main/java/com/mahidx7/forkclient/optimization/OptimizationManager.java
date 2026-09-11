package com.mahidx7.forkclient.optimization;

import com.mahidx7.forkclient.ForkClient;
import com.mahidx7.forkclient.optimization.chunk.ChunkBuildScheduler;
import com.mahidx7.forkclient.optimization.chunk.TaskOutput;
import com.mahidx7.forkclient.optimization.occlusion.OcclusionCuller;
import com.mahidx7.forkclient.optimization.region.RenderRegion;
import com.mahidx7.forkclient.optimization.region.RenderRegionManager;
import com.mahidx7.forkclient.optimization.storage.SectionStorage;
import com.mahidx7.forkclient.optimization.world.ClonedChunkSectionCache;
import com.mahidx7.forkclient.optimization.world.LevelSliceView;
import java.util.List;

/**
 * Central lifecycle coordinator for the backend optimization system.
 * Manages initialization, per-frame updates, and shutdown of all subsystems:
 * chunk build scheduler, occlusion culler, render region manager, frame budget,
 * and world data snapshots.
 */
public final class OptimizationManager {

    public static final OptimizationManager INSTANCE = new OptimizationManager();

    private final FrameBudgetManager frameBudget = new FrameBudgetManager();
    private final SectionStorage sectionStorage = new SectionStorage();
    private final ChunkBuildScheduler chunkScheduler = new ChunkBuildScheduler();
    private final RenderRegionManager regionManager = new RenderRegionManager();
    private final ClonedChunkSectionCache sectionCache = new ClonedChunkSectionCache();

    private OcclusionCuller occlusionCuller;
    private boolean initialized = false;
    private boolean enabled = true;

    private net.minecraft.world.level.Level currentLevel;
    private double lastCameraX, lastCameraY, lastCameraZ;
    private float lastFogEnd = 256.0f;

    private OptimizationManager() {
    }

    /**
     * Initializes all optimization subsystems. Called once on client entry.
     */
    public void initialize() {
        if (initialized) return;

        chunkScheduler.start();
        initialized = true;

        ForkClient.LOGGER.info("Fork Client optimization system initialized (workers: {})",
                chunkScheduler.getWorkerCount());
    }

    /**
     * Called each frame to begin the frame timing cycle.
     */
    public void beginFrame() {
        if (!enabled) return;
        frameBudget.beginFrame();
    }

    /**
     * Called each frame after terrain setup to collect and process chunk build results.
     */
    public void processChunkBuilds() {
        if (!enabled) return;

        chunkScheduler.collectResults();
        List<TaskOutput> results = chunkScheduler.drainPendingResults();

        if (!results.isEmpty()) {
            long uploadStart = System.nanoTime();
            regionManager.uploadResults(results);
            long uploadDuration = System.nanoTime() - uploadStart;
            frameBudget.recordUpload(uploadDuration);
        }
    }

    /**
     * Returns true if chunk uploads still have budget remaining this frame.
     */
    public boolean hasUploadBudget() {
        return enabled && frameBudget.hasUploadBudget();
    }

    /**
     * Schedules a chunk section for async meshing.
     */
    public void scheduleChunkRebuild(int sectionX, int sectionY, int sectionZ) {
        if (!enabled || currentLevel == null) return;

        SectionStorage.RenderSection section = sectionStorage.get(sectionX, sectionY, sectionZ);
        if (section == null) {
            section = new SectionStorage.RenderSection(sectionX, sectionY, sectionZ);
            sectionStorage.put(sectionX, sectionY, sectionZ, section);
        }

        if (!section.isDirty()) return;

        LevelSliceView slice = LevelSliceView.create(currentLevel, sectionX, sectionY, sectionZ, 2);
        chunkScheduler.scheduleMeshing(section, slice);
    }

    /**
     * Schedules chunk rebuilds for a block area. Called when blocks change.
     */
    public void scheduleRebuildForBlockArea(int minX, int minY, int minZ,
                                             int maxX, int maxY, int maxZ,
                                             boolean needsNeighborUpdate) {
        if (!enabled) return;

        int sectionMinX = minX >> 4;
        int sectionMinY = minY >> 4;
        int sectionMinZ = minZ >> 4;
        int sectionMaxX = maxX >> 4;
        int sectionMaxY = maxY >> 4;
        int sectionMaxZ = maxZ >> 4;

        for (int sx = sectionMinX; sx <= sectionMaxX; sx++) {
            for (int sy = sectionMinY; sy <= sectionMaxY; sy++) {
                for (int sz = sectionMinZ; sz <= sectionMaxZ; sz++) {
                    scheduleChunkRebuild(sx, sy, sz);
                }
            }
        }

        if (needsNeighborUpdate) {
            scheduleChunkRebuild(sectionMinX - 1, sectionMinY, sectionMinZ);
            scheduleChunkRebuild(sectionMaxX + 1, sectionMaxY, sectionMaxZ);
            scheduleChunkRebuild(sectionMinX, sectionMinY - 1, sectionMinZ);
            scheduleChunkRebuild(sectionMaxX, sectionMaxY + 1, sectionMaxZ);
            scheduleChunkRebuild(sectionMinX, sectionMinY, sectionMinZ - 1);
            scheduleChunkRebuild(sectionMaxX, sectionMaxY, sectionMaxZ + 1);
        }
    }

    /**
     * Performs visibility culling from the given camera position.
     */
    public void updateVisibility(double cameraX, double cameraY, double cameraZ,
                                  float viewDistance, float fogEnd) {
        if (!enabled) return;

        this.lastCameraX = cameraX;
        this.lastCameraY = cameraY;
        this.lastCameraZ = cameraZ;
        this.lastFogEnd = fogEnd;

        if (occlusionCuller == null) {
            occlusionCuller = new OcclusionCuller(sectionStorage, 2048, 256, 2048);
        }

        occlusionCuller.beginCull(cameraX, cameraY, cameraZ, viewDistance, fogEnd);

        if (occlusionCuller.isCullPending()) {
            occlusionCuller.executeCull(cameraX, cameraY, cameraZ, viewDistance, fogEnd);
        }
    }

    /**
     * Returns the sections that should be rendered this frame.
     */
    public List<SectionStorage.RenderSection> getVisibleSections() {
        if (occlusionCuller == null || !enabled) {
            return List.of();
        }
        return occlusionCuller.getVisibleSections();
    }

    /**
     * Returns regions containing visible sections for draw submission.
     */
    public List<RenderRegion> getVisibleRegions() {
        List<SectionStorage.RenderSection> visible = getVisibleSections();
        return regionManager.getRegionsForSections(visible);
    }

    /**
     * Marks a section as dirty, scheduling it for rebuild.
     */
    public void markSectionDirty(int sectionX, int sectionY, int sectionZ) {
        SectionStorage.RenderSection section = sectionStorage.get(sectionX, sectionY, sectionZ);
        if (section != null) {
            section.setDirty(true);
        }
    }

    /**
     * Sets the current world level for the optimization system.
     */
    public void setLevel(net.minecraft.world.level.Level level) {
        if (this.currentLevel != level) {
            this.currentLevel = level;
            sectionStorage.clear();
            sectionCache.clear();
            regionManager.clear();
            if (occlusionCuller != null) {
                occlusionCuller.getVisibilityTree().clear();
            }
        }
    }

    /**
     * Called when the renderer is reloaded (dimension change, etc.).
     */
    public void reload() {
        sectionStorage.clear();
        sectionCache.clear();
        regionManager.clear();
        frameBudget.reset();
        if (occlusionCuller != null) {
            occlusionCuller.getVisibilityTree().clear();
        }
    }

    /**
     * Returns true if the terrain render is complete (no pending rebuilds).
     */
    public boolean isTerrainRenderComplete() {
        return chunkScheduler.getPendingTaskCount() == 0
                && chunkScheduler.getPendingResultCount() == 0;
    }

    /**
     * Shuts down all optimization subsystems.
     */
    public void shutdown() {
        chunkScheduler.shutdown();
        sectionStorage.clear();
        sectionCache.clear();
        regionManager.clear();
        initialized = false;
        ForkClient.LOGGER.info("Fork Client optimization system shut down.");
    }

    public FrameBudgetManager getFrameBudget() { return frameBudget; }
    public SectionStorage getSectionStorage() { return sectionStorage; }
    public ChunkBuildScheduler getChunkScheduler() { return chunkScheduler; }
    public RenderRegionManager getRegionManager() { return regionManager; }
    public ClonedChunkSectionCache getSectionCache() { return sectionCache; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public double getLastCameraX() { return lastCameraX; }
    public double getLastCameraY() { return lastCameraY; }
    public double getLastCameraZ() { return lastCameraZ; }
    public float getLastFogEnd() { return lastFogEnd; }
}
