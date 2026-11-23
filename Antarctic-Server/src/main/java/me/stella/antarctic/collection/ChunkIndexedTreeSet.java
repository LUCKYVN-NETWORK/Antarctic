package me.stella.antarctic.collection;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * An ordered, hash-deduplicated set that additionally indexes its elements by the 16x16 chunk column that
 * contains them, so "everything inside this block-coordinate box" no longer scans the whole set.
 *
 * <p>Backed by an array-based binary min-heap (eliminating {@code TreeMap.Entry} allocations and red-black pointer
 * chasing), {@link ObjectArrayList} per-chunk buckets with O(1) swap-remove when elements implement {@link Indexed},
 * and an {@link ObjectOpenHashSet} for O(1) {@code equals}/{@code hashCode} deduplication (preserving vanilla's
 * {@code Block.isEqualTo} family equality).
 *
 * <p>Not thread safe; callers are main-thread confined.
 */
public final class ChunkIndexedTreeSet<E extends Comparable<? super E>> {

    /** Extracts the block coordinates that decide which chunk column an element belongs to. */
    public interface Locator<E> {
        int blockX(E element);

        int blockZ(E element);
    }

    /** Optional intrusive index handle enabling O(log n) arbitrary heap removal and O(1) swap-remove in chunk buckets. */
    public interface Indexed {
        int antarcticHeapIndex();

        void antarcticHeapIndex(int idx);

        int antarcticBucketIndex();

        void antarcticBucketIndex(int idx);
    }

    private final Locator<E> locator;
    private final ObjectOpenHashSet<E> hash = new ObjectOpenHashSet<E>();
    private Object[] heap = new Object[64];
    private int heapSize = 0;
    private final Long2ObjectOpenHashMap<ObjectArrayList<E>> buckets = new Long2ObjectOpenHashMap<ObjectArrayList<E>>();
    private final ObjectArrayList<E> scratch = new ObjectArrayList<E>();

    public ChunkIndexedTreeSet(Locator<E> locator) {
        this.locator = locator;
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
    }

    public int size() {
        return this.hash.size();
    }

    public boolean isEmpty() {
        return this.heapSize == 0;
    }

    public boolean contains(Object element) {
        return this.hash.contains(element);
    }

    /** @return false (and no change) when an {@code equals} element is already present */
    public boolean add(E element) {
        if (!this.hash.add(element)) {
            return false;
        }
        this.heapInsert(element);
        long key = chunkKey(this.locator.blockX(element) >> 4, this.locator.blockZ(element) >> 4);
        ObjectArrayList<E> bucket = this.buckets.get(key);
        if (bucket == null) {
            bucket = new ObjectArrayList<E>(4);
            this.buckets.put(key, bucket);
        }
        int bucketIdx = bucket.size();
        bucket.add(element);
        if (element instanceof Indexed) {
            ((Indexed) element).antarcticBucketIndex(bucketIdx);
        }
        return true;
    }

    /** @return true if an element was removed */
    public boolean remove(E element) {
        E stored = this.hash.get(element);
        if (stored == null) {
            return false;
        }
        this.hash.remove(stored);
        this.heapRemove(stored);
        long key = chunkKey(this.locator.blockX(stored) >> 4, this.locator.blockZ(stored) >> 4);
        ObjectArrayList<E> bucket = this.buckets.get(key);
        if (bucket != null) {
            this.bucketRemove(bucket, stored);
            if (bucket.isEmpty()) {
                this.buckets.remove(key);
            }
        }
        return true;
    }

    /** @throws NoSuchElementException when empty, like the old tree set */
    @SuppressWarnings("unchecked")
    public E first() {
        if (this.heapSize == 0) {
            throw new NoSuchElementException();
        }
        return (E) this.heap[0];
    }

    public void clear() {
        for (int i = 0; i < this.heapSize; ++i) {
            Object obj = this.heap[i];
            if (obj instanceof Indexed) {
                Indexed indexed = (Indexed) obj;
                indexed.antarcticHeapIndex(-1);
                indexed.antarcticBucketIndex(-1);
            }
            this.heap[i] = null;
        }
        this.heapSize = 0;
        this.hash.clear();
        this.buckets.clear();
    }

    /**
     * Collects every element whose block X is in {@code [minX, maxX)} and block Z is in {@code [minZ, maxZ)}, in
     * {@code compareTo} order, optionally removing them.
     *
     * @return the matches in order, or {@code null} when nothing matched (the contract of the old query)
     */
    public List<E> collect(int minX, int minZ, int maxX, int maxZ, boolean remove) {
        if (maxX <= minX || maxZ <= minZ || this.buckets.isEmpty()) {
            return null;
        }
        ObjectArrayList<E> found = this.scratch;
        found.clear();
        int chunkMinX = minX >> 4;
        int chunkMaxX = (maxX - 1) >> 4;
        int chunkMinZ = minZ >> 4;
        int chunkMaxZ = (maxZ - 1) >> 4;
        long span = ((long) chunkMaxX - chunkMinX + 1L) * ((long) chunkMaxZ - chunkMinZ + 1L);

        if (span <= this.buckets.size()) {
            for (int cx = chunkMinX; cx <= chunkMaxX; ++cx) {
                for (int cz = chunkMinZ; cz <= chunkMaxZ; ++cz) {
                    ObjectArrayList<E> bucket = this.buckets.get(chunkKey(cx, cz));
                    if (bucket != null) {
                        this.gather(bucket, minX, minZ, maxX, maxZ);
                    }
                }
            }
        } else {
            // The box covers more chunk columns than exist; walk the populated buckets instead.
            for (Long2ObjectMap.Entry<ObjectArrayList<E>> entry : this.buckets.long2ObjectEntrySet()) {
                long key = entry.getLongKey();
                int cx = (int) key;
                int cz = (int) (key >>> 32);
                if (cx >= chunkMinX && cx <= chunkMaxX && cz >= chunkMinZ && cz <= chunkMaxZ) {
                    this.gather(entry.getValue(), minX, minZ, maxX, maxZ);
                }
            }
        }

        int count = found.size();
        if (count == 0) {
            return null;
        }
        if (count > 1) {
            found.sort(null); // natural order == the old tree set's iteration order
        }
        ArrayList<E> result = new ArrayList<E>(count);
        for (int i = 0; i < count; ++i) {
            E element = found.get(i);
            result.add(element);
            if (remove) {
                this.remove(element);
            }
        }
        found.clear();
        return result;
    }

    private void gather(ObjectArrayList<E> bucket, int minX, int minZ, int maxX, int maxZ) {
        for (int i = 0, len = bucket.size(); i < len; ++i) {
            E element = bucket.get(i);
            int x = this.locator.blockX(element);
            if (x >= minX && x < maxX) {
                int z = this.locator.blockZ(element);
                if (z >= minZ && z < maxZ) {
                    this.scratch.add(element);
                }
            }
        }
    }

    // --------------------------------------------------------------------------------- binary min-heap

    private void heapInsert(E element) {
        int idx = this.heapSize;
        if (idx == this.heap.length) {
            this.heap = Arrays.copyOf(this.heap, idx << 1);
        }
        this.heapSize = idx + 1;
        this.siftUp(idx, element);
    }

    @SuppressWarnings("unchecked")
    private void heapRemove(E element) {
        int idx = -1;
        if (element instanceof Indexed) {
            int candidate = ((Indexed) element).antarcticHeapIndex();
            if (candidate >= 0 && candidate < this.heapSize && this.heap[candidate] == element) {
                idx = candidate;
            }
        } else if (this.heapSize > 0 && this.heap[0] == element) {
            idx = 0;
        }
        if (idx < 0) {
            for (int i = 0; i < this.heapSize; ++i) {
                if (this.heap[i] == element) {
                    idx = i;
                    break;
                }
            }
        }
        if (idx < 0) {
            return;
        }
        if (element instanceof Indexed) {
            ((Indexed) element).antarcticHeapIndex(-1);
        }
        int lastIdx = --this.heapSize;
        E last = (E) this.heap[lastIdx];
        this.heap[lastIdx] = null;
        if (idx < lastIdx) {
            int parentIdx = (idx - 1) >>> 1;
            if (idx > 0 && last.compareTo((E) this.heap[parentIdx]) < 0) {
                this.siftUp(idx, last);
            } else {
                this.siftDown(idx, last);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void siftUp(int idx, E element) {
        Object[] arr = this.heap;
        while (idx > 0) {
            int parentIdx = (idx - 1) >>> 1;
            E parent = (E) arr[parentIdx];
            if (element.compareTo(parent) >= 0) {
                break;
            }
            arr[idx] = parent;
            if (parent instanceof Indexed) {
                ((Indexed) parent).antarcticHeapIndex(idx);
            }
            idx = parentIdx;
        }
        arr[idx] = element;
        if (element instanceof Indexed) {
            ((Indexed) element).antarcticHeapIndex(idx);
        }
    }

    @SuppressWarnings("unchecked")
    private void siftDown(int idx, E element) {
        Object[] arr = this.heap;
        int size = this.heapSize;
        int half = size >>> 1;
        while (idx < half) {
            int childIdx = (idx << 1) + 1;
            E child = (E) arr[childIdx];
            int rightIdx = childIdx + 1;
            if (rightIdx < size) {
                E right = (E) arr[rightIdx];
                if (right.compareTo(child) < 0) {
                    childIdx = rightIdx;
                    child = right;
                }
            }
            if (element.compareTo(child) <= 0) {
                break;
            }
            arr[idx] = child;
            if (child instanceof Indexed) {
                ((Indexed) child).antarcticHeapIndex(idx);
            }
            idx = childIdx;
        }
        arr[idx] = element;
        if (element instanceof Indexed) {
            ((Indexed) element).antarcticHeapIndex(idx);
        }
    }

    // ----------------------------------------------------------------------------- O(1) bucket remove

    private void bucketRemove(ObjectArrayList<E> bucket, E element) {
        int size = bucket.size();
        int idx = -1;
        if (element instanceof Indexed) {
            int candidate = ((Indexed) element).antarcticBucketIndex();
            if (candidate >= 0 && candidate < size && bucket.get(candidate) == element) {
                idx = candidate;
            }
        }
        if (idx < 0) {
            for (int i = 0; i < size; ++i) {
                if (bucket.get(i) == element) {
                    idx = i;
                    break;
                }
            }
        }
        if (idx < 0) {
            return;
        }
        if (element instanceof Indexed) {
            ((Indexed) element).antarcticBucketIndex(-1);
        }
        int lastIdx = size - 1;
        if (idx < lastIdx) {
            E last = bucket.get(lastIdx);
            bucket.set(idx, last);
            if (last instanceof Indexed) {
                ((Indexed) last).antarcticBucketIndex(idx);
            }
        }
        bucket.remove(lastIdx);
    }
}
