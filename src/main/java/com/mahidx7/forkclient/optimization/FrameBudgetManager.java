package com.mahidx7.forkclient.optimization;

/**
 * Tracks frame timing using an exponential moving average and computes an adaptive
 * upload budget so chunk rebuild work is spread across frames to prevent stutter.
 * Targets roughly 30% of the average frame duration (minimum 10ms) for GPU upload processing.
 */
public final class FrameBudgetManager {

    private static final double SMOOTHING = 0.95;
    private static final double UPLOAD_BUDGET_FRACTION = 0.30;
    private static final long MIN_BUDGET_NS = 10_000_000L;
    private static final long MAX_BUDGET_NS = 50_000_000L;

    private double averageFrameTimeNs = 16_000_000.0;
    private long lastFrameTimeNs = System.nanoTime();

    private int maxChunkBuildsPerFrame = 4;
    private int maxChunkUploadsPerFrame = 8;
    private long currentUploadBudgetNs;
    private long uploadsThisFrame;
    private long uploadTimeThisFrame;

    public void beginFrame() {
        long now = System.nanoTime();
        long elapsed = now - lastFrameTimeNs;
        lastFrameTimeNs = now;

        if (elapsed > 0 && elapsed < 200_000_000L) {
            averageFrameTimeNs = averageFrameTimeNs * SMOOTHING + elapsed * (1.0 - SMOOTHING);
        }

        currentUploadBudgetNs = (long) (averageFrameTimeNs * UPLOAD_BUDGET_FRACTION);
        currentUploadBudgetNs = Math.max(MIN_BUDGET_NS, Math.min(MAX_BUDGET_NS, currentUploadBudgetNs));

        adaptChunkLimits();
        uploadsThisFrame = 0;
        uploadTimeThisFrame = 0;
    }

    private void adaptChunkLimits() {
        double fps = 1_000_000_000.0 / averageFrameTimeNs;
        if (fps > 90) {
            maxChunkBuildsPerFrame = 8;
            maxChunkUploadsPerFrame = 16;
        } else if (fps > 60) {
            maxChunkBuildsPerFrame = 4;
            maxChunkUploadsPerFrame = 8;
        } else if (fps > 30) {
            maxChunkBuildsPerFrame = 2;
            maxChunkUploadsPerFrame = 4;
        } else {
            maxChunkBuildsPerFrame = 1;
            maxChunkUploadsPerFrame = 2;
        }
    }

    public boolean hasUploadBudget() {
        return uploadsThisFrame < maxChunkUploadsPerFrame
                && uploadTimeThisFrame < currentUploadBudgetNs;
    }

    public void recordUpload(long durationNs) {
        uploadsThisFrame++;
        uploadTimeThisFrame += durationNs;
    }

    public int getMaxChunkBuildsPerFrame() {
        return maxChunkBuildsPerFrame;
    }

    public int getMaxChunkUploadsPerFrame() {
        return maxChunkUploadsPerFrame;
    }

    public long getUploadBudgetNs() {
        return currentUploadBudgetNs;
    }

    public long getAverageFrameTimeNs() {
        return (long) averageFrameTimeNs;
    }

    public double getFps() {
        return 1_000_000_000.0 / averageFrameTimeNs;
    }

    public void reset() {
        averageFrameTimeNs = 16_000_000.0;
        lastFrameTimeNs = System.nanoTime();
        maxChunkBuildsPerFrame = 4;
        maxChunkUploadsPerFrame = 8;
        currentUploadBudgetNs = MIN_BUDGET_NS;
        uploadsThisFrame = 0;
        uploadTimeThisFrame = 0;
    }
}
