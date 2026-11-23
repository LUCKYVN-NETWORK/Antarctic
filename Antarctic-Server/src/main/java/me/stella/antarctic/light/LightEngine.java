package me.stella.antarctic.light;

import java.util.Arrays;

/**
 * Computes sky and block light of the target columns of a {@link LightRegion} from scratch: the unique fixed point of the
 * per-cell rule documented in {@link LightRules}. Pure and stateless (every call allocates its own scratch, or uses a
 * caller-owned worker-local {@link Scratch}), so any number of threads may run it concurrently on separate regions.
 *
 * <p>How it works: light only ever decreases by at least 1 per cell, so the fixed point is the maximum over all paths from
 * a source. Sources are emitting cells (block light), cells that see the sky (sky light) and the fixed light of ring
 * columns. A flood fill that processes level 15 down to 2 raises every cell exactly to its final value the first time it
 * touches it, because the cost of entering a cell does not depend on where the light came from and level 1 can never
 * propagate further (cost >= 1).
 *
 * <p>Ring and absent columns are never written. Absent columns read as 0, so a target cell next to one is lit only from
 * what is present; callers that care (the relight service) must treat those border cells as unverified.
 *
 * <p>Antarctic - rebuilt from the design of Starlight (Spottedleaf / PaperMC; LGPL-3.0): propagate from sources, then take
 * neighbours' light into account at chunk edges. No code is copied.
 */
public final class LightEngine {

    /**
     * Reusable worker-local BFS bucket scratch. Pools only the {@code int[16][]} level queues (reset by zeroing counts,
     * never {@code Arrays.fill}-ing working arrays so TLAB allocation stays fast in young gen). Not thread-safe; keep one
     * instance per worker thread.
     */
    public static final class Scratch {
        final int[][] queue = new int[16][];
        final int[] queued = new int[16];
    }

    public LightResult relight(LightRegion region) {
        return this.relight(region, null);
    }

    public LightResult relight(LightRegion region, Scratch scratch) {
        LightResult result = new LightResult(region);

        if (region.hasSkyLight()) {
            new Pass(region, true, scratch).run(result);
        }

        new Pass(region, false, scratch).run(result);
        return result;
    }

    private static final class Pass {

        private final LightRegion region;
        private final boolean sky;
        private final int width;
        private final int depth;
        private final int blocksX;
        private final int blocksZ;
        /** Per target column and section a byte per cell holding the light computed so far; null = still implicit. */
        private final byte[][] work;
        private final int[][] queue;
        private final int[] queued;
        private final boolean reusableQueue;
        private long raised;

        Pass(LightRegion region, boolean sky, Scratch scratch) {
            this.region = region;
            this.sky = sky;
            this.width = region.width();
            this.depth = region.depth();
            this.blocksX = this.width << 4;
            this.blocksZ = this.depth << 4;
            this.work = new byte[region.columnCount() * LightRules.SECTIONS][];
            if (scratch != null) {
                this.queue = scratch.queue;
                this.queued = scratch.queued;
                Arrays.fill(this.queued, 0);
                this.reusableQueue = true;
            } else {
                this.queue = new int[16][];
                this.queued = new int[16];
                this.reusableQueue = false;
            }
        }

        void run(LightResult out) {
            for (int ci = 0; ci < this.region.columnCount(); ++ci) {
                if (this.region.isTarget(ci)) {
                    int baseX = ci % this.width << 4;
                    int baseZ = ci / this.width << 4;

                    if (this.sky) {
                        this.seedSky(ci, this.region.column(ci), baseX, baseZ);
                    } else {
                        this.seedBlock(ci, this.region.column(ci), baseX, baseZ);
                    }
                }
            }

            this.seedFromRing();
            this.propagate();
            this.collect(out);
            out.addRaised(this.raised);
        }

        // ------------------------------------------------------------------------------------------ storage

        private int implicit(LightColumn column, int y, int localX, int localZ) {
            return this.sky && y >= column.height(localX, localZ) ? LightRules.MAX_LIGHT : 0;
        }

        /** Light at region block coordinates; y must be 0..255. Absent columns and anything outside the region read 0. */
        private int get(int rx, int y, int rz) {
            if (rx < 0 || rz < 0 || rx >= this.blocksX || rz >= this.blocksZ) {
                return 0;
            }

            int ci = (rz >> 4) * this.width + (rx >> 4);
            LightColumn column = this.region.column(ci);

            if (column == null) {
                return 0;
            }

            int localX = rx & 15;
            int localZ = rz & 15;
            int section = y >> 4;

            if (this.region.isTarget(ci)) {
                byte[] light = this.work[ci * LightRules.SECTIONS + section];

                return light != null ? light[LightRules.cellIndex(localX, y & 15, localZ)] : this.implicit(column, y, localX, localZ);
            }

            return column.stored(this.sky, localX, y, localZ);
        }

        /** The working array of a target section, created on first write with the implicit values filled in. */
        private byte[] ensure(int ci, int section) {
            int key = ci * LightRules.SECTIONS + section;
            byte[] light = this.work[key];

            if (light == null) {
                light = new byte[LightRules.SECTION_CELLS];
                if (this.sky) {
                    LightColumn column = this.region.column(ci);
                    int baseY = section << 4;

                    for (int index = 0; index < LightRules.SECTION_CELLS; ++index) {
                        if (baseY + (index >> 8) >= column.height(index & 15, index >> 4 & 15)) {
                            light[index] = LightRules.MAX_LIGHT;
                        }
                    }
                }

                this.work[key] = light;
            }

            return light;
        }

        private void push(int level, int rx, int y, int rz) {
            int[] entries = this.queue[level];
            int size = this.queued[level];

            if (entries == null) {
                entries = this.queue[level] = new int[256];
            } else if (size == entries.length) {
                entries = this.queue[level] = Arrays.copyOf(entries, size << 1);
            }

            entries[size] = rx | rz << 12 | y << 24;
            this.queued[level] = size + 1;
        }

        /** Raises a target cell to {@code candidate} if that is higher than its current value, and queues it when > 1. */
        private void raise(int ci, LightColumn column, int rx, int y, int rz, int candidate) {
            int localX = rx & 15;
            int localZ = rz & 15;
            int section = y >> 4;
            int index = LightRules.cellIndex(localX, y & 15, localZ);
            byte[] light = this.work[ci * LightRules.SECTIONS + section];
            int current = light != null ? light[index] : this.implicit(column, y, localX, localZ);

            if (candidate > current) {
                if (light == null) {
                    light = this.ensure(ci, section);
                }

                light[index] = (byte) candidate;
                ++this.raised;
                if (candidate > 1) {
                    this.push(candidate, rx, y, rz);
                }
            }
        }

        // ---------------------------------------------------------------------------------------- seeding

        private void seedBlock(int ci, LightColumn column, int baseX, int baseZ) {
            for (int section = 0; section < LightRules.SECTIONS; ++section) {
                byte[] cells = column.cells(section);

                if (cells == null) {
                    continue;
                }

                for (int index = 0; index < LightRules.SECTION_CELLS; ++index) {
                    int emission = (cells[index] & 0xFF) >>> 4;

                    if (emission > 0) {
                        this.ensure(ci, section)[index] = (byte) emission;
                        ++this.raised;
                        if (emission > 1) {
                            this.push(emission, baseX + (index & 15), section << 4 | index >> 8, baseZ + (index >> 4 & 15));
                        }
                    }
                }
            }
        }

        private void seedSky(int ci, LightColumn column, int baseX, int baseZ) {
            for (int localZ = 0; localZ < 16; ++localZ) {
                for (int localX = 0; localX < 16; ++localX) {
                    int height = column.height(localX, localZ);
                    int rx = baseX + localX;
                    int rz = baseZ + localZ;

                    if (height > 255) {
                        // The whole column is under an opaque-ish top block: vanilla still reads 15 above y = 255.
                        int cost = LightRules.cost(column.cell(localX, 255, localZ));

                        if (cost < LightRules.BLOCKED) {
                            this.raise(ci, column, rx, 255, rz, LightRules.MAX_LIGHT - cost);
                        }

                        continue;
                    }

                    // Cells at or above the height are sources worth queueing only where they have a non-source neighbour:
                    // the cell right at the height (the one below it is not a source) and cells beside a taller target column.
                    int tallest = Math.max(Math.max(this.targetHeight(rx + 1, rz), this.targetHeight(rx - 1, rz)),
                            Math.max(this.targetHeight(rx, rz + 1), this.targetHeight(rx, rz - 1)));
                    int from = Math.max(height, 0);
                    int to = Math.min(255, Math.max(height, tallest - 1));

                    for (int y = from; y <= to; ++y) {
                        this.push(LightRules.MAX_LIGHT, rx, y, rz);
                    }
                }
            }
        }

        private int targetHeight(int rx, int rz) {
            if (rx < 0 || rz < 0 || rx >= this.blocksX || rz >= this.blocksZ) {
                return 0;
            }

            int ci = (rz >> 4) * this.width + (rx >> 4);

            return this.region.isTarget(ci) ? this.region.column(ci).height(rx & 15, rz & 15) : 0;
        }

        /** Ring columns are fixed light sources for the target cells that touch them. */
        private void seedFromRing() {
            for (int ci = 0; ci < this.region.columnCount(); ++ci) {
                if (this.region.isTarget(ci)) {
                    int cx = ci % this.width;
                    int cz = ci / this.width;

                    this.pullEdge(ci, cx, cz, -1, 0);
                    this.pullEdge(ci, cx, cz, 1, 0);
                    this.pullEdge(ci, cx, cz, 0, -1);
                    this.pullEdge(ci, cx, cz, 0, 1);
                }
            }
        }

        private void pullEdge(int ci, int cx, int cz, int dx, int dz) {
            int ncx = cx + dx;
            int ncz = cz + dz;

            if (ncx < 0 || ncz < 0 || ncx >= this.width || ncz >= this.depth) {
                return;
            }

            int nci = ncz * this.width + ncx;

            if (this.region.column(nci) == null || this.region.isTarget(nci)) {
                return; // absent contributes nothing; another target is reached by the flood fill itself
            }

            LightColumn column = this.region.column(ci);

            for (int t = 0; t < 16; ++t) {
                int localX = dx < 0 ? 0 : (dx > 0 ? 15 : t);
                int localZ = dz < 0 ? 0 : (dz > 0 ? 15 : t);
                int rx = (cx << 4) + localX;
                int rz = (cz << 4) + localZ;

                for (int y = 0; y < LightRules.WORLD_HEIGHT; ++y) {
                    int light = this.get(rx + dx, y, rz + dz);

                    if (light > 1) {
                        int cost = LightRules.cost(column.cell(localX, y, localZ));

                        if (cost < LightRules.BLOCKED && light - cost > 0) {
                            this.raise(ci, column, rx, y, rz, light - cost);
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------------------------------------- propagation

        private void propagate() {
            // Level 1 cells can never raise any neighbour since LightRules.cost >= 1.
            for (int level = 15; level >= 2; --level) {
                int count = this.queued[level];
                int[] entries = this.queue[level];

                for (int i = 0; i < count; ++i) {
                    int packed = entries[i];
                    int rx = packed & 4095;
                    int rz = packed >> 12 & 4095;
                    int y = packed >>> 24;

                    if (this.get(rx, y, rz) != level) {
                        continue; // raised again after it was queued; handled from its higher bucket
                    }

                    this.relax(level, rx + 1, y, rz);
                    this.relax(level, rx - 1, y, rz);
                    this.relax(level, rx, y, rz + 1);
                    this.relax(level, rx, y, rz - 1);
                    if (y < 255) {
                        this.relax(level, rx, y + 1, rz);
                    }

                    if (y > 0) {
                        this.relax(level, rx, y - 1, rz);
                    }
                }

                if (this.reusableQueue) {
                    this.queued[level] = 0;
                } else {
                    this.queue[level] = null; // new entries only ever go to lower levels
                }
            }
        }

        private void relax(int level, int rx, int y, int rz) {
            if (rx < 0 || rz < 0 || rx >= this.blocksX || rz >= this.blocksZ) {
                return;
            }

            int ci = (rz >> 4) * this.width + (rx >> 4);

            if (!this.region.isTarget(ci)) {
                return; // ring and absent columns are fixed
            }

            LightColumn column = this.region.column(ci);
            int cost = LightRules.cost(column.cell(rx & 15, y, rz & 15));

            if (cost < LightRules.BLOCKED && level - cost > 0) {
                this.raise(ci, column, rx, y, rz, level - cost);
            }
        }

        // ---------------------------------------------------------------------------------------- output

        private void collect(LightResult out) {
            for (int ci = 0; ci < this.region.columnCount(); ++ci) {
                if (!this.region.isTarget(ci)) {
                    continue;
                }

                LightColumn column = this.region.column(ci);
                int minH = Integer.MAX_VALUE;
                int maxH = Integer.MIN_VALUE;
                boolean heightsComputed = false;

                for (int section = 0; section < LightRules.SECTIONS; ++section) {
                    byte[] light = this.work[ci * LightRules.SECTIONS + section];

                    if (light == null) {
                        if (column.cells(section) == null) {
                            continue; // missing section and nothing spilled into it: the implicit value is right
                        }

                        // Untouched existing section: emit packed nibbles directly without allocating a 4 KiB work array.
                        byte[] nibbles = new byte[LightRules.NIBBLE_BYTES];
                        if (this.sky) {
                            if (!heightsComputed) {
                                int[] hm = column.heightMap();
                                for (int idx = 0; idx < 256; ++idx) {
                                    int h = hm[idx];
                                    if (h < minH) {
                                        minH = h;
                                    }
                                    if (h > maxH) {
                                        maxH = h;
                                    }
                                }
                                heightsComputed = true;
                            }
                            int baseY = section << 4;
                            if (baseY >= maxH) {
                                Arrays.fill(nibbles, (byte) 0xFF);
                            } else if (baseY + 15 >= minH) {
                                int[] hm = column.heightMap();
                                for (int ly = 0; ly < 16; ++ly) {
                                    int y = baseY + ly;
                                    int rowBase = ly << 7;
                                    for (int lz = 0; lz < 16; ++lz) {
                                        int hBase = lz << 4;
                                        int byteBase = rowBase | (lz << 3);
                                        for (int lx = 0; lx < 16; lx += 2) {
                                            int lo = y >= hm[hBase | lx] ? 15 : 0;
                                            int hi = y >= hm[hBase | (lx + 1)] ? 15 : 0;
                                            nibbles[byteBase | (lx >> 1)] = (byte) (lo | (hi << 4));
                                        }
                                    }
                                }
                            }
                        }
                        out.put(this.sky, ci, section, nibbles);
                        continue;
                    }

                    byte[] nibbles = new byte[LightRules.NIBBLE_BYTES];

                    for (int i = 0; i < LightRules.NIBBLE_BYTES; ++i) {
                        nibbles[i] = (byte) (light[i << 1] & 15 | (light[i << 1 | 1] & 15) << 4);
                    }

                    out.put(this.sky, ci, section, nibbles);
                }
            }
        }
    }
}
