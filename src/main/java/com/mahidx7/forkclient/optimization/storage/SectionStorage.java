package com.mahidx7.forkclient.optimization.storage;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores and manages {@link RenderSection} instances, indexed by packed section coordinates.
 * Provides fast lookup for section lifecycle decisions and rebuild scheduling.
 */
public final class SectionStorage {

    private final ConcurrentHashMap<Long, RenderSection> sections = new ConcurrentHashMap<>();

    public void put(int x, int y, int z, RenderSection section) {
        sections.put(packKey(x, y, z), section);
    }

    public RenderSection get(int x, int y, int z) {
        return sections.get(packKey(x, y, z));
    }

    public RenderSection remove(int x, int y, int z) {
        return sections.remove(packKey(x, y, z));
    }

    public void clear() {
        sections.clear();
    }

    public int size() {
        return sections.size();
    }

    public Iterable<RenderSection> all() {
        return sections.values();
    }

    private static long packKey(int x, int y, int z) {
        long k = ((long) x & 0x3FFFFFF) | (((long) y & 0xFFFF) << 26) | (((long) z & 0x3FFFFFF) << 42);
        return k != 0 ? k : 1L;
    }

    /**
     * Represents a single chunk section (16x16x16 blocks) that is managed by the
     * optimization system. Tracks its build state, dirty status, and render data.
     */
    public static final class RenderSection {
        public static final int FLAG_BLOCK_GEOMETRY = 1;
        public static final int FLAG_BLOCK_ENTITIES = 2;
        public static final int FLAG_ANIMATED_SPRITES = 4;
        public static final int FLAG_IS_BUILT = 8;

        public final int sectionX;
        public final int sectionY;
        public final int sectionZ;

        private volatile int flags;
        private volatile boolean dirty = true;
        private volatile long lastBuildTime;
        private volatile int neighborMask;

        public RenderSection(int sectionX, int sectionY, int sectionZ) {
            this.sectionX = sectionX;
            this.sectionY = sectionY;
            this.sectionZ = sectionZ;
        }

        public boolean hasFlag(int flag) {
            return (flags & flag) != 0;
        }

        public void setFlag(int flag, boolean value) {
            if (value) {
                flags |= flag;
            } else {
                flags &= ~flag;
            }
        }

        public int getFlags() { return flags; }
        public void setFlags(int flags) { this.flags = flags; }

        public boolean isDirty() { return dirty; }
        public void setDirty(boolean dirty) { this.dirty = dirty; }

        public long getLastBuildTime() { return lastBuildTime; }
        public void setLastBuildTime(long time) { this.lastBuildTime = time; }

        public int getNeighborMask() { return neighborMask; }
        public void setNeighborMask(int mask) { this.neighborMask = mask; }

        public double squaredDistanceTo(int x, int y, int z) {
            double dx = (sectionX * 16 + 8) - x;
            double dy = (sectionY * 16 + 8) - y;
            double dz = (sectionZ * 16 + 8) - z;
            return dx * dx + dy * dy + dz * dz;
        }

        public int getBlockX() { return sectionX * 16; }
        public int getBlockY() { return sectionY * 16; }
        public int getBlockZ() { return sectionZ * 16; }
    }
}
