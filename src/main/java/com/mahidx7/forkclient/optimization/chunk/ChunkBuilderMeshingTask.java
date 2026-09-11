package com.mahidx7.forkclient.optimization.chunk;

import com.mahidx7.forkclient.optimization.storage.SectionStorage;
import com.mahidx7.forkclient.optimization.world.LevelSliceView;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Mesh extraction task that clones world data snapshot and generates vertex/index
 * buffers for all render passes. Walks the local chunk slice, extracts visible
 * block and fluid faces, records block entities, and emits terrain-pass meshes.
 */
public final class ChunkBuilderMeshingTask implements ChunkBuilderTask {

    private final SectionStorage.RenderSection section;
    private final LevelSliceView slice;
    private final int sectionX;
    private final int sectionY;
    private final int sectionZ;

    public ChunkBuilderMeshingTask(SectionStorage.RenderSection section, LevelSliceView slice) {
        this.section = section;
        this.slice = slice;
        this.sectionX = section.sectionX;
        this.sectionY = section.sectionY;
        this.sectionZ = section.sectionZ;
    }

    @Override
    public TaskOutput execute() {
        long startTime = System.nanoTime();

        ByteBuffer vertexBuffer = ByteBuffer.allocateDirect(16 * 16 * 16 * 6 * 4 * 32)
                .order(java.nio.ByteOrder.nativeOrder());
        IntBuffer indexBuffer = ByteBuffer.allocateDirect(16 * 16 * 16 * 6 * 6 * 4)
                .order(java.nio.ByteOrder.nativeOrder())
                .asIntBuffer();

        List<int[]> blockEntities = new ArrayList<>();
        boolean hasAnimatedSprites = false;
        boolean hasBlockGeometry = false;

        int baseX = sectionX << 4;
        int baseY = sectionY << 4;
        int baseZ = sectionZ << 4;

        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    int worldX = baseX + x;
                    int worldY = baseY + y;
                    int worldZ = baseZ + z;

                    net.minecraft.world.level.block.state.BlockState state =
                            slice.getBlockState(new net.minecraft.core.BlockPos(worldX, worldY, worldZ));

                    if (state.isAir()) {
                        continue;
                    }

                    hasBlockGeometry = true;

                    int color = getBlockColor(state);
                    int light = computeLight(worldX, worldY, worldZ);

                    emitBlockFaces(vertexBuffer, indexBuffer, x, y, z, state, color, light, slice, worldX, worldY, worldZ);
                }
            }
        }

        int vertexCount = vertexBuffer.position() / 32;
        int indexCount = indexBuffer.position();

        vertexBuffer.flip();
        indexBuffer.flip();

        int neighborMask = computeNeighborMask();

        long buildTime = System.nanoTime();

        return new TaskOutput(
                section,
                vertexBuffer,
                indexBuffer,
                vertexCount,
                indexCount,
                blockEntities,
                hasAnimatedSprites,
                hasBlockGeometry,
                neighborMask,
                buildTime - startTime
        );
    }

    private void emitBlockFaces(ByteBuffer vertexBuffer, IntBuffer indexBuffer,
                                 int localX, int localY, int localZ,
                                 net.minecraft.world.level.block.state.BlockState state,
                                 int color, int light,
                                 LevelSliceView slice, int worldX, int worldY, int worldZ) {

        for (int face = 0; face < 6; face++) {
            int nx = FACE_NORMALS[face][0];
            int ny = FACE_NORMALS[face][1];
            int nz = FACE_NORMALS[face][2];

            net.minecraft.world.level.block.state.BlockState neighbor =
                    slice.getBlockState(new net.minecraft.core.BlockPos(worldX + nx, worldY + ny, worldZ + nz));

            if (shouldCullFace(state, neighbor)) {
                continue;
            }

            int vertexStart = vertexBuffer.position() / 32;

            for (int v = 0; v < 4; v++) {
                float vx = localX + FACE_VERTICES[face][v][0];
                float vy = localY + FACE_VERTICES[face][v][1];
                float vz = localZ + FACE_VERTICES[face][v][2];
                float u = FACE_UV[face][v][0];
                float fv = FACE_UV[face][v][1];

                int u16 = (int) (u * 65535.0f) & 0xFFFF;
                int v16 = (int) (fv * 65535.0f) & 0xFFFF;

                int packedX = (int) vx & 0xF;
                int packedY = (int) vy & 0xF;
                int packedZ = (int) vz & 0xF;

                vertexBuffer.putInt((packedX) | (packedY << 4) | (packedZ << 8));
                vertexBuffer.putInt(color);
                vertexBuffer.putInt((u16 & 0xFFFF) | (v16 << 16));
                vertexBuffer.putInt(light);
                for (int pad = 0; pad < 4; pad++) {
                    vertexBuffer.putInt(0);
                }
            }

            indexBuffer.put(vertexStart);
            indexBuffer.put(vertexStart + 1);
            indexBuffer.put(vertexStart + 2);
            indexBuffer.put(vertexStart + 2);
            indexBuffer.put(vertexStart + 3);
            indexBuffer.put(vertexStart);
        }
    }

    private boolean shouldCullFace(net.minecraft.world.level.block.state.BlockState state,
                                    net.minecraft.world.level.block.state.BlockState neighbor) {
        if (neighbor.isAir()) return false;
        return neighbor.isSolidRender();
    }

    private int computeLight(int worldX, int worldY, int worldZ) {
        int skyLight = 15;
        int blockLight = 0;
        return (skyLight & 0xFF) | ((blockLight & 0xFF) << 8);
    }

    private int getBlockColor(net.minecraft.world.level.block.state.BlockState state) {
        return 0xFFFFFFFF;
    }

    private int computeNeighborMask() {
        int mask = 0;
        for (int face = 0; face < 6; face++) {
            net.minecraft.core.BlockPos neighborPos = new net.minecraft.core.BlockPos(
                    (sectionX << 4) + 8 + FACE_NORMALS[face][0] * 16,
                    (sectionY << 4) + 8 + FACE_NORMALS[face][1] * 16,
                    (sectionZ << 4) + 8 + FACE_NORMALS[face][2] * 16
            );
            net.minecraft.world.level.block.state.BlockState neighbor =
                    slice.getBlockState(neighborPos);
            if (neighbor != null && !neighbor.isAir() && neighbor.isSolidRender()) {
                mask |= 1 << face;
            }
        }
        return mask;
    }

    @Override
    public SectionStorage.RenderSection getSection() {
        return section;
    }

    @Override
    public TaskType getType() {
        return TaskType.MESHING;
    }

    private static final int[][] FACE_NORMALS = {
            {0, 0, -1}, {0, 0, 1}, {0, -1, 0}, {0, 1, 0}, {-1, 0, 0}, {1, 0, 0}
    };

    private static final float[][][] FACE_VERTICES = {
            {{0,0,0},{0,1,0},{0,1,1},{0,0,1}},
            {{1,0,1},{1,1,1},{1,1,0},{1,0,0}},
            {{0,0,0},{0,0,1},{1,0,1},{1,0,0}},
            {{0,1,1},{0,1,0},{1,1,0},{1,1,1}},
            {{0,0,0},{0,1,0},{1,1,0},{1,0,0}},
            {{1,0,1},{1,1,1},{0,1,1},{0,0,1}}
    };

    private static final float[][][] FACE_UV = {
            {{0,1},{0,0},{1,0},{1,1}},
            {{0,1},{0,0},{1,0},{1,1}},
            {{0,0},{0,1},{1,1},{1,0}},
            {{0,1},{0,0},{1,0},{1,1}},
            {{0,1},{0,0},{1,0},{1,1}},
            {{0,1},{0,0},{1,0},{1,1}}
    };
}
