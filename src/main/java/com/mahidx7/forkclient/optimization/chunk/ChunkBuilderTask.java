package com.mahidx7.forkclient.optimization.chunk;

import com.mahidx7.forkclient.optimization.storage.SectionStorage;

/**
 * Interface for chunk build tasks that can be executed by worker threads.
 */
public interface ChunkBuilderTask {

    TaskOutput execute();

    SectionStorage.RenderSection getSection();

    TaskType getType();

    enum TaskType {
        MESHING,
        SORTING
    }
}
