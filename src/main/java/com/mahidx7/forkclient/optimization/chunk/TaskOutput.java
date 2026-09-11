package com.mahidx7.forkclient.optimization.chunk;

import com.mahidx7.forkclient.optimization.storage.SectionStorage;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;

/**
 * Output result from a chunk build task, containing the generated geometry data
 * and metadata about the build.
 */
public record TaskOutput(
        SectionStorage.RenderSection section,
        ByteBuffer vertexData,
        IntBuffer indexData,
        int vertexCount,
        int indexCount,
        List<int[]> blockEntities,
        boolean hasAnimatedSprites,
        boolean hasBlockGeometry,
        int neighborMask,
        long buildTimeNanos
) {
}
