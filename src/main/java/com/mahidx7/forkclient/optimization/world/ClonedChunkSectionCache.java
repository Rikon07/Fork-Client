package com.mahidx7.forkclient.optimization.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * LRU cache of cloned 16x16x16 section data blocks, reused across meshing tasks
 * to avoid redundant world reads. Entries are keyed by the section's packed coordinate.
 */
public final class ClonedChunkSectionCache {

    private static final int MAX_ENTRIES = 512;

    private final Long2ObjectLinkedOpenHashMap<LevelChunkSection> cache = new Long2ObjectLinkedOpenHashMap<>(MAX_ENTRIES);

    public ClonedChunkSectionCache() {
        this.cache.defaultReturnValue(null);
    }

    /**
     * Returns a cloned copy of the section at the given section coordinates,
     * or null if not cached.
     */
    public LevelChunkSection get(int sectionX, int sectionY, int sectionZ) {
        long key = packKey(sectionX, sectionY, sectionZ);
        LevelChunkSection section = cache.getAndMoveToFirst(key);
        if (section != null) {
            return section.copy();
        }
        return null;
    }

    /**
     * Stores a cloned copy of the section into the cache.
     */
    public void put(int sectionX, int sectionY, int sectionZ, LevelChunkSection section) {
        if (section == null) {
            return;
        }
        long key = packKey(sectionX, sectionY, sectionZ);
        cache.putAndMoveToFirst(key, section.copy());
        while (cache.size() > MAX_ENTRIES) {
            cache.removeLast();
        }
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    private static long packKey(int x, int y, int z) {
        long k = ((long) x & 0x3FFFFFF) | (((long) y & 0xFFFF) << 26) | (((long) z & 0x3FFFFFF) << 42);
        return k != 0 ? k : 1L;
    }
}
