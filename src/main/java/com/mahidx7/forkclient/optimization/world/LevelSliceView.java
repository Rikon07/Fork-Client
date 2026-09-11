package com.mahidx7.forkclient.optimization.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Thread-safe read-only snapshot of a world region. Backed by cloned section data
 * so worker threads can safely read world state without contending with the main
 * game thread. Uses hardcoded overworld height bounds for MC 26.2.
 */
public final class LevelSliceView {

    private static final int WORLD_BOTTOM = -64;
    private static final int WORLD_TOP = 320;

    private final LevelChunkSection[] sections;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final int widthSections;
    private final int heightSections;
    private final int depthSections;

    public LevelSliceView(LevelChunkSection[] sections, int originX, int originY, int originZ,
                          int widthSections, int heightSections, int depthSections) {
        this.sections = sections;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.widthSections = widthSections;
        this.heightSections = heightSections;
        this.depthSections = depthSections;
    }

    public static LevelSliceView create(net.minecraft.world.level.Level level,
                                         int centerSectionX, int centerSectionY, int centerSectionZ,
                                         int radius) {
        int w = radius * 2 + 1;
        int worldBottom = WORLD_BOTTOM >> 4;
        int worldTop = (WORLD_TOP - 1) >> 4;
        int h = Math.min(16, worldTop - worldBottom + 1);
        int d = radius * 2 + 1;

        int ox = centerSectionX - radius;
        int oy = centerSectionY - (h / 2);
        int oz = centerSectionZ - radius;

        oy = Math.max(oy, worldBottom);
        if (oy + h > worldTop + 1) {
            oy = worldTop + 1 - h;
        }

        LevelChunkSection[] sections = new LevelChunkSection[w * h * d];

        for (int sx = 0; sx < w; sx++) {
            for (int sy = 0; sy < h; sy++) {
                for (int sz = 0; sz < d; sz++) {
                    int worldSectionX = ox + sx;
                    int worldSectionY = oy + sy;
                    int worldSectionZ = oz + sz;

                    LevelChunkSection section = getSection(level, worldSectionX, worldSectionY, worldSectionZ);
                    sections[(sx * h + sy) * d + sz] = section;
                }
            }
        }

        return new LevelSliceView(sections, ox, oy, oz, w, h, d);
    }

    private static LevelChunkSection getSection(net.minecraft.world.level.Level level,
                                                 int sectionX, int sectionY, int sectionZ) {
        try {
            int worldBottom = WORLD_BOTTOM >> 4;
            int worldTop = (WORLD_TOP - 1) >> 4;
            if (sectionY < worldBottom || sectionY > worldTop) {
                return null;
            }
            LevelChunk chunk = level.getChunk(sectionX, sectionZ);
            return chunk.getSections()[sectionY - worldBottom];
        } catch (Exception e) {
            return null;
        }
    }

    public BlockState getBlockState(BlockPos pos) {
        int localX = (pos.getX() >> 4) - originX;
        int localY = (pos.getY() >> 4) - originY;
        int localZ = (pos.getZ() >> 4) - originZ;

        if (localX < 0 || localX >= widthSections
                || localY < 0 || localY >= heightSections
                || localZ < 0 || localZ >= depthSections) {
            return Blocks.AIR.defaultBlockState();
        }

        LevelChunkSection section = sections[(localX * heightSections + localY) * depthSections + localZ];
        if (section == null) {
            return Blocks.AIR.defaultBlockState();
        }

        int intraX = pos.getX() & 15;
        int intraY = pos.getY() & 15;
        int intraZ = pos.getZ() & 15;
        return section.getBlockState(intraX, intraY, intraZ);
    }

    public int getOriginX() { return originX; }
    public int getOriginY() { return originY; }
    public int getOriginZ() { return originZ; }
    public int getWidthSections() { return widthSections; }
    public int getHeightSections() { return heightSections; }
    public int getDepthSections() { return depthSections; }

    public boolean containsSection(int sectionX, int sectionY, int sectionZ) {
        int lx = sectionX - originX;
        int ly = sectionY - originY;
        int lz = sectionZ - originZ;
        return lx >= 0 && lx < widthSections
                && ly >= 0 && ly < heightSections
                && lz >= 0 && lz < depthSections;
    }

    public LevelChunkSection getSectionDirect(int localX, int localY, int localZ) {
        if (localX < 0 || localX >= widthSections
                || localY < 0 || localY >= heightSections
                || localZ < 0 || localZ >= depthSections) {
            return null;
        }
        return sections[(localX * heightSections + localY) * depthSections + localZ];
    }
}
