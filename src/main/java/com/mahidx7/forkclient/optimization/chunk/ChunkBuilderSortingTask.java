package com.mahidx7.forkclient.optimization.chunk;

import com.mahidx7.forkclient.optimization.storage.SectionStorage;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;

/**
 * Re-sorts translucent quads based on the current camera position to ensure
 * correct alpha blending. This task is triggered when the camera moves and
 * previously built sections contain translucent geometry.
 */
public final class ChunkBuilderSortingTask implements ChunkBuilderTask {

    private final SectionStorage.RenderSection section;
    private final ByteBuffer existingVertexBuffer;
    private final IntBuffer existingIndexBuffer;
    private final int vertexCount;
    private final int indexCount;
    private final double cameraX;
    private final double cameraY;
    private final double cameraZ;

    public ChunkBuilderSortingTask(SectionStorage.RenderSection section,
                                    ByteBuffer vertexBuffer, IntBuffer indexBuffer,
                                    int vertexCount, int indexCount,
                                    double cameraX, double cameraY, double cameraZ) {
        this.section = section;
        this.existingVertexBuffer = vertexBuffer;
        this.existingIndexBuffer = indexBuffer;
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
    }

    @Override
    public TaskOutput execute() {
        long startTime = System.nanoTime();

        ByteBuffer sortedVertexBuffer = ByteBuffer.allocateDirect(existingVertexBuffer.capacity())
                .order(java.nio.ByteOrder.nativeOrder());
        IntBuffer sortedIndexBuffer = ByteBuffer.allocateDirect(existingIndexBuffer.capacity() * 4)
                .order(java.nio.ByteOrder.nativeOrder())
                .asIntBuffer();

        existingVertexBuffer.position(0);
        existingIndexBuffer.position(0);

        sortedVertexBuffer.put(existingVertexBuffer);
        sortedVertexBuffer.flip();

        int quadCount = indexCount / 6;
        if (quadCount <= 1) {
            sortedIndexBuffer.put(existingIndexBuffer);
            sortedIndexBuffer.flip();

            long sortTime = System.nanoTime() - startTime;
            return new TaskOutput(
                    section, sortedVertexBuffer, sortedIndexBuffer,
                    vertexCount, indexCount, List.of(),
                    false, true, 0, sortTime
            );
        }

        float[] depths = new float[quadCount];
        int[] quadOrder = new int[quadCount];

        int baseX = section.sectionX << 4;
        int baseY = section.sectionY << 4;
        int baseZ = section.sectionZ << 4;

        for (int q = 0; q < quadCount; q++) {
            quadOrder[q] = q;
            int baseVertex = q * 4;
            float cx = 0, cy = 0, cz = 0;
            for (int v = 0; v < 4; v++) {
                int offset = (baseVertex + v) * 32;
                existingVertexBuffer.position(offset);
                int packed = existingVertexBuffer.getInt();
                float vx = (packed & 0xF) + baseX;
                float vy = ((packed >> 4) & 0xF) + baseY;
                float vz = ((packed >> 8) & 0xF) + baseZ;
                cx += vx; cy += vy; cz += vz;
            }
            cx /= 4; cy /= 4; cz /= 4;
            float dx = cx - (float) cameraX;
            float dy = cy - (float) cameraY;
            float dz = cz - (float) cameraZ;
            depths[q] = dx * dx + dy * dy + dz * dz;
        }

        for (int i = quadCount - 1; i > 0; i--) {
            for (int j = 0; j < i; j++) {
                if (depths[quadOrder[j]] < depths[quadOrder[j + 1]]) {
                    int tmp = quadOrder[j];
                    quadOrder[j] = quadOrder[j + 1];
                    quadOrder[j + 1] = tmp;
                }
            }
        }

        existingVertexBuffer.position(0);
        for (int q = 0; q < quadCount; q++) {
            int srcQuad = quadOrder[q];
            int srcOffset = srcQuad * 4 * 32;
            existingVertexBuffer.position(srcOffset);
            byte[] quadData = new byte[4 * 32];
            existingVertexBuffer.get(quadData);
            sortedVertexBuffer.put(quadData);

            int srcIndex = srcQuad * 6;
            existingIndexBuffer.position(srcIndex);
            for (int i = 0; i < 6; i++) {
                sortedIndexBuffer.put(existingIndexBuffer.get());
            }
        }

        sortedVertexBuffer.flip();
        sortedIndexBuffer.flip();

        long sortTime = System.nanoTime() - startTime;
        return new TaskOutput(
                section, sortedVertexBuffer, sortedIndexBuffer,
                vertexCount, indexCount, List.of(),
                false, true, 0, sortTime
        );
    }

    @Override
    public SectionStorage.RenderSection getSection() {
        return section;
    }

    @Override
    public TaskType getType() {
        return TaskType.SORTING;
    }
}
