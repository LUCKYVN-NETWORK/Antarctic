package me.stella.antarctic.collection;

import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Remembers light checks that vanilla discards because the 3x3 chunk neighborhood of the changed block is not fully
 * loaded, so they can be replayed once the neighbors arrive. Entries are keyed by the chunk that contains the block and
 * store only chunk-local data ({@code x & 15}, y, {@code z & 15}, sky/block flag), which fits one int.
 *
 * <p>A light check is level-triggered (it recomputes what the light at that position should be), so replaying an old
 * entry later is harmless. Entries replay in the order they were first recorded, against the block states as they are
 * at replay time; vanilla's incremental algorithm is path dependent (see DeferredLightUpdateTest), so the result equals
 * running the same checks after all of the block changes, not after each one. The registry is capped; once full, new entries are dropped (and counted),
 * which is exactly what vanilla does for every entry. Not thread safe; main-thread confined.
 *
 * <p>Antarctic - original work; the "never save or light a chunk with pending light updates" rule is the one Starlight
 * documents (Spottedleaf / PaperMC; LGPL-3.0), used here only as a design reference.
 */
public final class PendingLightRegistry {

    /** What a replay should do with a chunk that has pending entries. */
    public interface Gate {
        int READY = 0;
        /** The neighborhood is incomplete again; keep the entries and wait for the next load event. */
        int WAIT = 1;
        /** The chunk is gone; discard its entries. */
        int GONE = 2;

        int check(long chunkKey);
    }

    public interface Visitor {
        void accept(long chunkKey, int localX, int y, int localZ, boolean sky);
    }

    private final Long2ObjectOpenHashMap<IntLinkedOpenHashSet> pending = new Long2ObjectOpenHashMap<IntLinkedOpenHashSet>();
    private final LongArrayFIFOQueue ready = new LongArrayFIFOQueue();
    private final LongOpenHashSet readySet = new LongOpenHashSet();
    private final int maxEntries;
    private int total;
    private long dropped;
    private int[] buffer = new int[256];

    public PendingLightRegistry(int maxEntries) {
        this.maxEntries = Math.max(0, maxEntries);
    }

    public static int pack(int localX, int y, int localZ, boolean sky) {
        return (sky ? 1 << 16 : 0) | (y & 255) << 8 | (localZ & 15) << 4 | (localX & 15);
    }

    public static int unpackX(int packed) {
        return packed & 15;
    }

    public static int unpackZ(int packed) {
        return packed >> 4 & 15;
    }

    public static int unpackY(int packed) {
        return packed >> 8 & 255;
    }

    public static boolean unpackSky(int packed) {
        return (packed >> 16 & 1) != 0;
    }

    /** @return true when the entry is stored (false: duplicate, or dropped because the registry is full) */
    public boolean add(long chunkKey, int packed) {
        IntLinkedOpenHashSet set = this.pending.get(chunkKey);
        if (set != null && set.contains(packed)) {
            return false;
        }
        if (this.total >= this.maxEntries) {
            ++this.dropped;
            return false;
        }
        if (set == null) {
            set = new IntLinkedOpenHashSet(8);
            this.pending.put(chunkKey, set);
        }
        set.add(packed);
        ++this.total;
        return true;
    }

    public boolean hasPending(long chunkKey) {
        return !this.pending.isEmpty() && this.pending.containsKey(chunkKey);
    }

    /** Queue a chunk for replay if it has entries and is not already queued. */
    public void markReady(long chunkKey) {
        if (this.pending.containsKey(chunkKey) && this.readySet.add(chunkKey)) {
            this.ready.enqueue(chunkKey);
        }
    }

    /** Forget a chunk (it unloaded). */
    public void clearChunk(long chunkKey) {
        IntLinkedOpenHashSet set = this.pending.remove(chunkKey);
        if (set != null) {
            this.total -= set.size();
        }
        this.readySet.remove(chunkKey);
        // The key may still sit in the ready queue; drain() skips keys without entries.
    }

    /**
     * Replays up to {@code budget} entries from queued chunks, oldest queued chunk first.
     *
     * @return the number of entries replayed
     */
    public int drain(int budget, Gate gate, Visitor visitor) {
        int processed = 0;
        while (processed < budget && !this.ready.isEmpty()) {
            long key = this.ready.firstLong();
            IntLinkedOpenHashSet set = this.pending.get(key);
            if (set == null || !this.readySet.contains(key)) { // cleared or already handled
                this.ready.dequeueLong();
                this.readySet.remove(key);
                continue;
            }
            int state = gate.check(key);
            if (state == Gate.GONE) {
                this.clearChunk(key);
                this.ready.dequeueLong();
                continue;
            }
            if (state == Gate.WAIT) {
                this.ready.dequeueLong();
                this.readySet.remove(key);
                continue;
            }
            int take = Math.min(budget - processed, set.size());
            if (this.buffer.length < take) {
                this.buffer = new int[Math.max(take, this.buffer.length * 2)];
            }
            for (int i = 0; i < take; ++i) {
                this.buffer[i] = set.removeFirstInt(); // insertion order: checks replay in the order they were first recorded
            }
            this.total -= take;
            if (set.isEmpty()) {
                this.pending.remove(key);
                this.ready.dequeueLong();
                this.readySet.remove(key);
            }
            // Visit after bookkeeping, so a visitor that records new entries cannot disturb the iteration above.
            for (int i = 0; i < take; ++i) {
                int packed = this.buffer[i];
                visitor.accept(key, unpackX(packed), unpackY(packed), unpackZ(packed), unpackSky(packed));
            }
            processed += take;
        }
        return processed;
    }

    public int size() {
        return this.total;
    }

    public int pendingChunks() {
        return this.pending.size();
    }

    public long dropped() {
        return this.dropped;
    }
}
