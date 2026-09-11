package com.mahidx7.forkclient.optimization.storage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Sub-allocator for GPU vertex/index buffer memory within each render region.
 * Avoids per-section buffer allocation by managing a contiguous arena of memory
 * divided into segments. Supports resize with copy-on-grow when a region runs
 * out of space, and tracks free/used segments for defragmentation.
 */
public final class GlBufferArena {

    private static final int INITIAL_CAPACITY = 1024 * 1024;
    private static final int GROWTH_FACTOR = 2;

    private ByteBuffer buffer;
    private int capacity;
    private int used;
    private Segment freeHead;
    private Segment usedHead;

    public GlBufferArena() {
        this(INITIAL_CAPACITY);
    }

    public GlBufferArena(int initialCapacity) {
        this.capacity = Math.max(initialCapacity, 4096);
        this.buffer = ByteBuffer.allocateDirect(this.capacity).order(ByteOrder.nativeOrder());
        this.used = 0;
        this.freeHead = new Segment(0, this.capacity, null);
        this.usedHead = null;
    }

    /**
     * Allocates a contiguous block of the given size from the arena.
     *
     * @param sizeBytes number of bytes to allocate
     * @return an Allocation handle containing offset and size, or null if out of space
     */
    public synchronized Allocation alloc(int sizeBytes) {
        if (sizeBytes <= 0) {
            return null;
        }

        int aligned = align(sizeBytes);
        Segment free = findBestFit(aligned);

        if (free == null) {
            grow(aligned);
            free = findBestFit(aligned);
            if (free == null) {
                return null;
            }
        }

        removeFree(free);

        if (free.size > aligned) {
            insertFree(new Segment(free.offset + aligned, free.size - aligned, null));
        }

        Allocation alloc = new Allocation(free.offset, aligned);
        insertUsed(new Segment(free.offset, aligned, null));
        used += aligned;
        return alloc;
    }

    /**
     * Returns a previously allocated segment to the free list.
     */
    public synchronized void free(Allocation allocation) {
        if (allocation == null) {
            return;
        }

        removeUsed(allocation.offset, allocation.size);
        insertFree(new Segment(allocation.offset, allocation.size, null));
        used -= allocation.size;
        mergeFreeSegments();
    }

    public synchronized void clear() {
        freeHead = new Segment(0, capacity, null);
        usedHead = null;
        used = 0;
    }

    public int getUsed() { return used; }
    public int getCapacity() { return capacity; }
    public ByteBuffer getBuffer() { return buffer; }

    private Segment findBestFit(int size) {
        Segment best = null;
        Segment curr = freeHead;
        while (curr != null) {
            if (curr.size >= size) {
                if (best == null || curr.size < best.size) {
                    best = curr;
                }
            }
            curr = curr.next;
        }
        return best;
    }

    private void grow(int minAdditional) {
        int newCapacity = capacity * GROWTH_FACTOR;
        while (newCapacity - capacity < minAdditional) {
            newCapacity *= GROWTH_FACTOR;
        }

        ByteBuffer newBuffer = ByteBuffer.allocateDirect(newCapacity).order(ByteOrder.nativeOrder());
        buffer.position(0);
        newBuffer.put(buffer);
        newBuffer.position(0);

        insertFree(new Segment(capacity, newCapacity - capacity, null));
        buffer = newBuffer;
        capacity = newCapacity;
    }

    private void mergeFreeSegments() {
        // Sort free segments by offset using a simple array approach
        int count = 0;
        Segment curr = freeHead;
        while (curr != null) {
            count++;
            curr = curr.next;
        }
        if (count <= 1) return;

        Segment[] arr = new Segment[count];
        curr = freeHead;
        for (int i = 0; i < count; i++) {
            arr[i] = curr;
            curr = curr.next;
        }

        java.util.Arrays.sort(arr, (a, b) -> Integer.compare(a.offset, b.offset));

        freeHead = null;
        Segment tail = null;
        for (int i = 0; i < count; i++) {
            if (tail != null && tail.offset + tail.size == arr[i].offset) {
                tail.size += arr[i].size;
            } else {
                arr[i].next = null;
                if (freeHead == null) {
                    freeHead = arr[i];
                } else {
                    tail.next = arr[i];
                }
                tail = arr[i];
            }
        }
    }

    private void insertFree(Segment seg) {
        seg.next = freeHead;
        freeHead = seg;
    }

    private void removeFree(Segment seg) {
        if (freeHead == seg) {
            freeHead = seg.next;
            return;
        }
        Segment prev = freeHead;
        while (prev != null && prev.next != seg) {
            prev = prev.next;
        }
        if (prev != null) {
            prev.next = seg.next;
        }
    }

    private void insertUsed(Segment seg) {
        seg.next = usedHead;
        usedHead = seg;
    }

    private void removeUsed(int offset, int size) {
        Segment prev = null;
        Segment curr = usedHead;
        while (curr != null) {
            if (curr.offset == offset && curr.size == size) {
                if (prev == null) {
                    usedHead = curr.next;
                } else {
                    prev.next = curr.next;
                }
                return;
            }
            prev = curr;
            curr = curr.next;
        }
    }

    private static int align(int size) {
        return (size + 15) & ~15;
    }

    public static final class Allocation {
        public final int offset;
        public final int size;

        public Allocation(int offset, int size) {
            this.offset = offset;
            this.size = size;
        }
    }

    private static final class Segment {
        int offset;
        int size;
        Segment next;

        Segment(int offset, int size, Segment next) {
            this.offset = offset;
            this.size = size;
            this.next = next;
        }
    }
}
