package com.mahidx7.forkclient.optimization.chunk;

import com.mahidx7.forkclient.optimization.storage.SectionStorage;
import com.mahidx7.forkclient.optimization.world.LevelSliceView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Manages the task queue and worker thread pool for async chunk compilation.
 * Handles task prioritization (nearby/visible chunks first) and result processing.
 * Tasks can be prioritized or deferred based on their distance from the camera
 * and the current frame budget.
 */
public final class ChunkBuildScheduler {

    private static final int MAX_WORKERS = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);

    private final BlockingQueue<ChunkBuilderTask> taskQueue = new LinkedBlockingQueue<>(256);
    private final BlockingQueue<TaskOutput> resultQueue = new LinkedBlockingQueue<>(128);
    private final ConcurrentLinkedQueue<TaskOutput> pendingResults = new ConcurrentLinkedQueue<>();

    private final List<ChunkBuilderThread> workers = new ArrayList<>();
    private volatile boolean active = false;

    public ChunkBuildScheduler() {
    }

    public void start() {
        if (active) return;
        active = true;

        for (int i = 0; i < MAX_WORKERS; i++) {
            ChunkBuilderThread worker = new ChunkBuilderThread(
                    "ForkOpt-ChunkBuilder-" + i, taskQueue, resultQueue
            );
            workers.add(worker);
            worker.start();
        }
    }

    public void shutdown() {
        active = false;
        for (ChunkBuilderThread worker : workers) {
            worker.shutdown();
        }
        workers.clear();
        taskQueue.clear();
    }

    /**
     * Schedules a meshing task for the given section. The task will be picked up
     * by the next available worker thread.
     */
    public void scheduleMeshing(SectionStorage.RenderSection section, LevelSliceView slice) {
        if (!active) return;
        try {
            ChunkBuilderMeshingTask task = new ChunkBuilderMeshingTask(section, slice);
            taskQueue.offer(task);
        } catch (IllegalStateException e) {
            // Queue full, skip this build
        }
    }

    /**
     * Schedules a sorting task for translucent geometry.
     */
    public void scheduleSorting(SectionStorage.RenderSection section,
                                 java.nio.ByteBuffer vertexBuffer,
                                 java.nio.IntBuffer indexBuffer,
                                 int vertexCount, int indexCount,
                                 double cameraX, double cameraY, double cameraZ) {
        if (!active) return;
        try {
            ChunkBuilderSortingTask task = new ChunkBuilderSortingTask(
                    section, vertexBuffer, indexBuffer, vertexCount, indexCount,
                    cameraX, cameraY, cameraZ
            );
            taskQueue.offer(task);
        } catch (IllegalStateException e) {
            // Queue full, skip this sort
        }
    }

    /**
     * Polls for completed tasks and adds them to the pending results queue.
     * Should be called from the main thread each frame.
     */
    public void collectResults() {
        TaskOutput output;
        while ((output = resultQueue.poll()) != null) {
            pendingResults.add(output);
        }
    }

    /**
     * Returns and removes all pending build results. Called from the main thread
     * to process completed chunk builds.
     */
    public List<TaskOutput> drainPendingResults() {
        List<TaskOutput> results = new ArrayList<>();
        TaskOutput output;
        while ((output = pendingResults.poll()) != null) {
            results.add(output);
        }
        return results;
    }

    public int getPendingTaskCount() {
        return taskQueue.size();
    }

    public int getPendingResultCount() {
        return pendingResults.size();
    }

    public int getWorkerCount() {
        return workers.size();
    }

    public boolean isActive() {
        return active;
    }
}
