package com.mahidx7.forkclient.optimization.chunk;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Worker thread that pulls chunk build tasks from a queue and executes them.
 * Multiple instances form the async chunk builder thread pool.
 */
public final class ChunkBuilderThread extends Thread {

    private final BlockingQueue<ChunkBuilderTask> taskQueue;
    private final BlockingQueue<TaskOutput> resultQueue;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public ChunkBuilderThread(String name, BlockingQueue<ChunkBuilderTask> taskQueue,
                               BlockingQueue<TaskOutput> resultQueue) {
        super(name);
        this.taskQueue = taskQueue;
        this.resultQueue = resultQueue;
        setDaemon(true);
        setPriority(Thread.NORM_PRIORITY - 1);
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                ChunkBuilderTask task = taskQueue.take();
                if (task == null) {
                    continue;
                }

                TaskOutput output = task.execute();
                if (output != null) {
                    resultQueue.put(output);
                }
            } catch (InterruptedException e) {
                if (!running.get()) {
                    break;
                }
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Log and continue - don't let one failed task kill the worker
            }
        }
    }

    public void shutdown() {
        running.set(false);
        interrupt();
    }

    public boolean isRunning() {
        return running.get();
    }
}
