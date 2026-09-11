package com.mahidx7.forkclient.optimization.vertex;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * Compact vertex format for chunk mesh data. Packs position, color, UV, and
 * light into 8 integers (32 bytes) per vertex, compared to vanilla's larger format.
 * Uses bit manipulation to minimize GPU memory bandwidth requirements.
 */
public final class ChunkMeshFormats {

    public static final int BYTES_PER_VERTEX = 32;
    public static final int INTS_PER_VERTEX = 8;
    public static final int MAX_VERTICES_PER_SECTION = 16 * 16 * 16 * 6 * 4;
    public static final int MAX_INDICES_PER_SECTION = 16 * 16 * 16 * 6 * 6;

    private ChunkMeshFormats() {
    }

    /**
     * Packs a vertex into the compact format.
     *
     * @param x local block x (0-15)
     * @param y local block y (0-15)
     * @param z local block z (0-15)
     * @param color packed ARGB color
     * @param u texture u (0-1 range mapped to 0-65535)
     * @param v texture v (0-1 range mapped to 0-65535)
     * @param light packed sky+block light (0-255 each packed into 16 bits)
     * @return packed vertex data as 8 ints
     */
    public static int[] packVertex(int x, int y, int z, int color, float u, float v, int light) {
        int u16 = (int) (u * 65535.0f) & 0xFFFF;
        int v16 = (int) (v * 65535.0f) & 0xFFFF;
        return new int[]{
                (x & 0xF) | ((y & 0xF) << 4) | ((z & 0xF) << 8),
                color,
                (u16 & 0xFFFF) | (v16 << 16),
                light,
                0, 0, 0, 0
        };
    }

    /**
     * Allocates a direct ByteBuffer for the maximum number of vertices in a section.
     */
    public static ByteBuffer allocateVertexBuffer() {
        return ByteBuffer.allocateDirect(MAX_VERTICES_PER_SECTION * BYTES_PER_VERTEX)
                .order(ByteOrder.nativeOrder());
    }

    /**
     * Allocates a direct IntBuffer for the maximum number of indices in a section.
     */
    public static IntBuffer allocateIndexBuffer() {
        return ByteBuffer.allocateDirect(MAX_INDICES_PER_SECTION * Integer.BYTES)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer();
    }
}
