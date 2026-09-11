package com.mahidx7.forkclient.optimization.occlusion;



/**
 * Spatial tree for fast visibility queries. Uses a compact tree structure
 * to quickly determine if a section at a given coordinate is marked as visible
 * and present in the world. Based on a traversable forest for hierarchical
 * visibility checks.
 */
public final class SectionTree {

    private static final int LEAF_BITS = 4;
    private static final int LEAF_SIZE = 1 << LEAF_BITS;

    private final int minX, minY, minZ;
    private final int sizeX, sizeY, sizeZ;
    private final int dimX, dimY, dimZ;
    private byte[] tree;
    private int treeSize;

    public SectionTree(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.dimX = (sizeX + LEAF_SIZE - 1) / LEAF_SIZE;
        this.dimY = (sizeY + LEAF_SIZE - 1) / LEAF_SIZE;
        this.dimZ = (sizeZ + LEAF_SIZE - 1) / LEAF_SIZE;
        this.tree = new byte[dimX * dimY * dimZ];
        this.treeSize = 0;
    }

    public void set(int x, int y, int z, boolean visible) {
        int lx = x - minX;
        int ly = y - minY;
        int lz = z - minZ;
        if (lx < 0 || lx >= sizeX || ly < 0 || ly >= sizeY || lz < 0 || lz >= sizeZ) {
            return;
        }

        int treeIdx = (lx >> LEAF_BITS) * dimY * dimZ + (ly >> LEAF_BITS) * dimZ + (lz >> LEAF_BITS);

        if (visible && tree[treeIdx] == 0) {
            treeSize++;
        } else if (!visible && tree[treeIdx] == 1) {
            treeSize--;
        }
        tree[treeIdx] = (byte) (visible ? 1 : 0);
    }

    public boolean isVisible(int x, int y, int z) {
        int lx = x - minX;
        int ly = y - minY;
        int lz = z - minZ;
        if (lx < 0 || lx >= sizeX || ly < 0 || ly >= sizeY || lz < 0 || lz >= sizeZ) {
            return false;
        }
        int treeIdx = (lx >> LEAF_BITS) * dimY * dimZ + (ly >> LEAF_BITS) * dimZ + (lz >> LEAF_BITS);
        return tree[treeIdx] != 0;
    }

    public int size() {
        return treeSize;
    }

    public void clear() {
        java.util.Arrays.fill(tree, (byte) 0);
        treeSize = 0;
    }

    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getSizeX() { return sizeX; }
    public int getSizeY() { return sizeY; }
    public int getSizeZ() { return sizeZ; }
}
