package me.stella.antarctic.collection;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * An ordered, hash-deduplicated set that additionally indexes its elements by the 16x16 chunk column that
 * contains them, so "everything inside this block-coordinate box" no longer scans the whole set.
 *
 * <p>Designed for the world's scheduled block tick list: it replaces a {@code HashTreeSet} (hash set for
 * {@code contains}, tree set for ordering) whose chunk-save query walked every pending tick in the world.
 * Not thread safe; callers are main-thread confined.
 *
 * <p>Semantics that matter for vanilla parity:
 * <ul>
 *   <li>{@link #contains(Object)} uses {@code equals}/{@code hashCode} (as the old hash set did).</li>
 *   <li>{@link #first()} and the order of {@link #collect} results follow {@code compareTo} (as the old tree set did).</li>
 *   <li>{@link #add(Comparable)} refuses an element that is {@code equals} to one already present, which is
 *       exactly what every caller used to enforce with a {@code contains} check before {@code add}.</li>
 * </ul>
 *
 * <p>Antarctic - original work; the per-chunk indexing idea is the same one modern Paper uses for its scheduled
 * tick lists (Spottedleaf / PaperMC).
 */
public final class ChunkIndexedTreeSet<E extends Comparable<? super E>> {

    /** Extracts the block coordinates that decide which chunk column an element belongs to. */
    public interface Locator<E> {
        int blockX(E element);

        int blockZ(E element);
    }

    private final Locator<E> locator;
    private final ObjectOpenHashSet<E> hash = new ObjectOpenHashSet<E>();
    private final TreeSet<E> tree = new TreeSet<E>();
    private final Long2ObjectOpenHashMap<ObjectOpenHashSet<E>> buckets = new Long2ObjectOpenHashMap<ObjectOpenHashSet<E>>();
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
        return this.hash.isEmpty();
    }

    public boolean contains(Object element) {
        return this.hash.contains(element);
    }

    /** @return false (and no change) when an {@code equals} element is already present */
    public boolean add(E element) {
        if (!this.hash.add(element)) {
            return false;
        }
        this.tree.add(element);
        long key = chunkKey(this.locator.blockX(element) >> 4, this.locator.blockZ(element) >> 4);
        ObjectOpenHashSet<E> bucket = this.buckets.get(key);
        if (bucket == null) {
            bucket = new ObjectOpenHashSet<E>(4);
            this.buckets.put(key, bucket);
        }
        bucket.add(element);
        return true;
    }

    /** @return true if an element was removed */
    public boolean remove(E element) {
        if (!this.hash.remove(element)) {
            return false;
        }
        this.tree.remove(element);
        long key = chunkKey(this.locator.blockX(element) >> 4, this.locator.blockZ(element) >> 4);
        ObjectOpenHashSet<E> bucket = this.buckets.get(key);
        if (bucket != null) {
            bucket.remove(element);
            if (bucket.isEmpty()) {
                this.buckets.remove(key);
            }
        }
        return true;
    }

    /** @throws java.util.NoSuchElementException when empty, like the old tree set */
    public E first() {
        return this.tree.first();
    }

    public void clear() {
        this.hash.clear();
        this.tree.clear();
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
                    ObjectOpenHashSet<E> bucket = this.buckets.get(chunkKey(cx, cz));
                    if (bucket != null) {
                        this.gather(bucket, minX, minZ, maxX, maxZ);
                    }
                }
            }
        } else {
            // The box covers more chunk columns than exist; walk the populated buckets instead.
            for (Long2ObjectMap.Entry<ObjectOpenHashSet<E>> entry : this.buckets.long2ObjectEntrySet()) {
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

    private void gather(ObjectOpenHashSet<E> bucket, int minX, int minZ, int maxX, int maxZ) {
        for (E element : bucket) {
            int x = this.locator.blockX(element);
            if (x >= minX && x < maxX) {
                int z = this.locator.blockZ(element);
                if (z >= minZ && z < maxZ) {
                    this.scratch.add(element);
                }
            }
        }
    }
}
