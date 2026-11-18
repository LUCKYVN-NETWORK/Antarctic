package me.stella.antarctic.collection;

import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;

/**
 * Remembers light checks that vanilla discards because the 3x3 chunk neighborhood of the changed block is not fully
 * loaded, so they can be replayed once the neighbors arrive. Entries are keyed by the chunk that contains the block and
 * store only chunk-local data ({@code x & 15}, y, {@code z & 15}, sky/block flag), which fits one int.
 *
 * <p>A light check is level-triggered (it recomputes what the light at that position should be), so replaying an old
 * entry later is harmless. Replay order is grouped per chunk: entries of one chunk keep the order in which they were
 * first recorded, and chunks replay in the order they became ready ({@link #markReady}). It is NOT a global recording
 * order: recording A1, B1, A2 and marking A then B ready replays A1, A2, B1. Replay runs against the block states as
 * they are at replay time; vanilla's incremental algorithm is path dependent (see DeferredLightUpdateTest), so the
 * result equals running the same checks after all of the block changes, not after each one. The registry is capped;
 * once full, new entries are dropped (and counted), which is exactly what vanilla does for every entry. Not thread
 * safe; main-thread confined.
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
    /**
     * Ready queue of chunk keys with a parallel queue of sequence numbers. {@link #queued} maps each chunk that has a
     * live slot to that slot's sequence number; a slot whose number no longer matches (the chunk was cleared after it
     * was queued) is stale and skipped. Sequence numbers are never reused, so a chunk that unloads and reloads cannot
     * revive its old, earlier position in the line, and no per-chunk state outlives its stale slot.
     */
    private final LongArrayFIFOQueue ready = new LongArrayFIFOQueue();
    private final LongArrayFIFOQueue readySeq = new LongArrayFIFOQueue();
    private final Long2LongOpenHashMap queued = new Long2LongOpenHashMap();
    private long nextSeq;
    private final int maxEntries;
    private int total;
    private long dropped;
    private int[] buffer = new int[256];

    public PendingLightRegistry(int maxEntries) {
        this.maxEntries = Math.max(0, maxEntries);
        this.queued.defaultReturnValue(-1L);
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
        if (this.pending.containsKey(chunkKey) && !this.queued.containsKey(chunkKey)) {
            long seq = this.nextSeq++;
            this.queued.put(chunkKey, seq);
            this.ready.enqueue(chunkKey);
            this.readySeq.enqueue(seq);
        }
    }

    /** Forget a chunk (it unloaded). Any slot it still holds in the ready queue becomes stale and is skipped. */
    public void clearChunk(long chunkKey) {
        IntLinkedOpenHashSet set = this.pending.remove(chunkKey);
        if (set != null) {
            this.total -= set.size();
        }
        this.queued.remove(chunkKey);
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
            long seq = this.readySeq.firstLong();
            if (this.queued.get(key) != seq) { // stale slot: the chunk was cleared after it was queued
                this.dequeueSlot();
                continue;
            }
            IntLinkedOpenHashSet set = this.pending.get(key);
            if (set == null) { // defensive: a live slot always has entries
                this.queued.remove(key);
                this.dequeueSlot();
                continue;
            }
            int state = gate.check(key);
            if (state == Gate.GONE) {
                this.clearChunk(key);
                this.dequeueSlot();
                continue;
            }
            if (state == Gate.WAIT) {
                this.queued.remove(key);
                this.dequeueSlot();
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
                this.queued.remove(key);
                this.dequeueSlot();
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

    private void dequeueSlot() {
        this.ready.dequeueLong();
        this.readySeq.dequeueLong();
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
