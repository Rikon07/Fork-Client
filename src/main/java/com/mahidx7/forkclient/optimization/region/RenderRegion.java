package com.mahidx7.forkclient.optimization.region;

import com.mahidx7.forkclient.optimization.storage.GlBufferArena;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a fixed-size volume (8x4x8 sections = 128x64x128 blocks) of the world.
 * Each region manages its own GPU buffer arena for vertex/index data and prepares
 * a batch that allows rendering all visible sections in the region with minimal
 * draw call overhead.
 *
 * Draw commands for all sections within a region are batched together to reduce
 * CPU overhead from per-section draw calls.
 */
public final class RenderRegion {

    public static final int REGION_SECTIONS_X = 8;
    public static final int REGION_SECTIONS_Y = 4;
    public static final int REGION_SECTIONS_Z = 8;
    public static final int SECTIONS_PER_REGION = REGION_SECTIONS_X * REGION_SECTIONS_Y * REGION_SECTIONS_Z;

    public final int regionX;
    public final int regionY;
    public final int regionZ;

    private final GlBufferArena vertexArena;
    private final GlBufferArena indexArena;
    private final SectionRenderData[] sectionData;
    private final List<Integer> dirtySectionIndices;
    private boolean batchDirty = true;

    public RenderRegion(int regionX, int regionY, int regionZ) {
        this.regionX = regionX;
        this.regionY = regionY;
        this.regionZ = regionZ;
        this.vertexArena = new GlBufferArena(512 * 1024);
        this.indexArena = new GlBufferArena(256 * 1024);
        this.sectionData = new SectionRenderData[SECTIONS_PER_REGION];
        this.dirtySectionIndices = new ArrayList<>();

        for (int i = 0; i < SECTIONS_PER_REGION; i++) {
            sectionData[i] = new SectionRenderData();
        }
    }

    /**
     * Returns the local index for a section within this region.
     */
    public int getLocalIndex(int sectionX, int sectionY, int sectionZ) {
        int lx = sectionX - regionX * REGION_SECTIONS_X;
        int ly = sectionY - regionY * REGION_SECTIONS_Y;
        int lz = sectionZ - regionZ * REGION_SECTIONS_Z;

        if (lx < 0 || lx >= REGION_SECTIONS_X
                || ly < 0 || ly >= REGION_SECTIONS_Y
                || lz < 0 || lz >= REGION_SECTIONS_Z) {
            return -1;
        }
        return (lx * REGION_SECTIONS_Y + ly) * REGION_SECTIONS_Z + lz;
    }

    /**
     * Uploads new geometry for a section into the region's buffer arenas.
     */
    public boolean uploadSectionGeometry(int sectionX, int sectionY, int sectionZ,
                                          ByteBuffer vertexData, IntBuffer indexData,
                                          int vertexCount, int indexCount) {
        int localIdx = getLocalIndex(sectionX, sectionY, sectionZ);
        if (localIdx < 0) return false;

        SectionRenderData data = sectionData[localIdx];

        if (data.vertexAllocation != null) {
            vertexArena.free(data.vertexAllocation);
        }
        if (data.indexAllocation != null) {
            indexArena.free(data.indexAllocation);
        }

        int vertexBytes = vertexCount * 32;
        int indexBytes = indexCount * 4;

        if (vertexBytes > 0) {
            data.vertexAllocation = vertexArena.alloc(vertexBytes);
            if (data.vertexAllocation != null) {
                ByteBuffer vertexBuffer = vertexArena.getBuffer();
                vertexBuffer.position(data.vertexAllocation.offset);
                vertexData.position(0);
                int oldLimit = vertexData.limit();
                vertexData.limit(Math.min(vertexBytes, vertexData.capacity()));
                vertexBuffer.put(vertexData);
                vertexData.limit(oldLimit);
            }
        }

        if (indexBytes > 0) {
            data.indexAllocation = indexArena.alloc(indexBytes);
            if (data.indexAllocation != null) {
                ByteBuffer indexBuffer = indexArena.getBuffer();
                indexBuffer.position(data.indexAllocation.offset);
                indexData.position(0);
                int oldLimit = indexData.limit();
                indexData.limit(Math.min(indexCount, indexData.capacity()));
                while (indexData.hasRemaining() && indexBuffer.remaining() >= 4) {
                    indexBuffer.putInt(indexData.get());
                }
                indexData.limit(oldLimit);
            }
        }

        data.vertexCount = vertexCount;
        data.indexCount = indexCount;
        data.hasGeometry = vertexCount > 0;
        data.valid = true;

        if (!dirtySectionIndices.contains(localIdx)) {
            dirtySectionIndices.add(localIdx);
        }
        batchDirty = true;

        return true;
    }

    /**
     * Invalidates all cached draw batch data, forcing a rebuild on next render.
     */
    public void clearAllCachedBatches() {
        batchDirty = true;
        for (SectionRenderData data : sectionData) {
            data.valid = false;
        }
    }

    /**
     * Returns the draw commands for all valid sections in this region.
     * Each command contains the vertex offset, index offset, and counts.
     */
    public List<DrawCommand> buildDrawCommands() {
        List<DrawCommand> commands = new ArrayList<>();

        for (int i = 0; i < SECTIONS_PER_REGION; i++) {
            SectionRenderData data = sectionData[i];
            if (data.hasGeometry && data.valid
                    && data.vertexAllocation != null && data.indexAllocation != null) {
                commands.add(new DrawCommand(
                        data.vertexAllocation.offset / 32,
                        data.indexAllocation.offset / 4,
                        data.vertexCount,
                        data.indexCount
                ));
            }
        }

        batchDirty = false;
        return commands;
    }

    public boolean isBatchDirty() { return batchDirty; }
    public GlBufferArena getVertexArena() { return vertexArena; }
    public GlBufferArena getIndexArena() { return indexArena; }
    public int getUsedVertexMemory() { return vertexArena.getUsed(); }
    public int getUsedIndexMemory() { return indexArena.getUsed(); }

    public void clear() {
        vertexArena.clear();
        indexArena.clear();
        for (SectionRenderData data : sectionData) {
            data.vertexAllocation = null;
            data.indexAllocation = null;
            data.vertexCount = 0;
            data.indexCount = 0;
            data.hasGeometry = false;
            data.valid = false;
        }
        dirtySectionIndices.clear();
        batchDirty = true;
    }

    /**
     * Per-section render data within a region.
     */
    public static final class SectionRenderData {
        public GlBufferArena.Allocation vertexAllocation;
        public GlBufferArena.Allocation indexAllocation;
        public int vertexCount;
        public int indexCount;
        public boolean hasGeometry;
        public boolean valid;
    }

    /**
     * A single draw command for a section within a region.
     */
    public record DrawCommand(
            int vertexOffset,
            int indexOffset,
            int vertexCount,
            int indexCount
    ) {
    }
}
