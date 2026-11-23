package me.stella.antarctic.light;

import java.util.Arrays;

/**
 * Checks whether the light stored in a target column satisfies the per-cell rule of {@link LightRules}, i.e. whether it
 * is the fixed point. This is the "enforcer" verification: it never recomputes or floods, it reads each cell and its six
 * neighbours (7 reads), so it costs far less than a relight and is written independently of {@link LightEngine}.
 *
 * <p>It transcribes vanilla's {@code World.a(BlockPosition, EnumSkyBlock)} exactly, including reading the cell itself for
 * the neighbour below y = 0 and 15 / 0 above y = 255, so that on any stored data (consistent or not) {@link #expected}
 * equals what the server's own rule would compute. A cell whose horizontal neighbour column is absent cannot be judged and
 * is skipped, never reported.
 */
public final class LightChecker {

    public enum Scope {
        /** Every cell of every section 0..15 of the column. */
        ALL,
        /** Only cells in the 60 edge columns (local x or z equal to 0 or 15); what an outside change can break. */
        BORDER
    }

    /** Outcome of one column check. */
    public static final class Report {

        private final int checked;
        private final int skipped;
        private final int violations;
        private final int[] positions;

        Report(int checked, int skipped, int violations, int[] positions) {
            this.checked = checked;
            this.skipped = skipped;
            this.violations = violations;
            this.positions = positions;
        }

        public int checked() {
            return this.checked;
        }

        /** Cells that could not be judged because a horizontal neighbour column is absent. */
        public int skipped() {
            return this.skipped;
        }

        public int violations() {
            return this.violations;
        }

        public boolean consistent() {
            return this.violations == 0;
        }

        /** The first violating cells, packed with {@link LightRules#packPosition} (sky flag in bit 16). */
        public int[] positions() {
            return this.positions.clone();
        }
    }

    private LightChecker() {
    }

    /**
     * Checks one target column.
     *
     * @param maxReported how many violating positions to remember (the count is always exact)
     */
    public static Report check(LightRegion region, int chunkX, int chunkZ, Scope scope, int maxReported) {
        int ci = region.index(chunkX, chunkZ);

        if (ci < 0 || !region.isTarget(ci)) {
            throw new IllegalArgumentException("chunk " + chunkX + "," + chunkZ + " is not a target column of the region");
        }

        LightColumn column = region.column(ci);
        LightColumn west = region.column(chunkX - 1, chunkZ);
        LightColumn east = region.column(chunkX + 1, chunkZ);
        LightColumn north = region.column(chunkX, chunkZ - 1);
        LightColumn south = region.column(chunkX, chunkZ + 1);

        // Above (maxActiveSection + 2) << 4, all 5 columns have null cells, null stored nibbles, and y >= height:
        // sky light is guaranteed consistent (15 == 15) and block light is guaranteed consistent (0 == 0, or skipped next to absent neighbour).
        int maxActive = highestActiveSection(column);
        maxActive = Math.max(maxActive, highestActiveSection(west));
        maxActive = Math.max(maxActive, highestActiveSection(east));
        maxActive = Math.max(maxActive, highestActiveSection(north));
        maxActive = Math.max(maxActive, highestActiveSection(south));
        int maxY = maxActive < 0 ? 0 : Math.min(LightRules.WORLD_HEIGHT, (maxActive + 2) << 4);
        int prunedLayers = LightRules.WORLD_HEIGHT - maxY;

        int colsInScope = scope == Scope.BORDER ? 60 : 256;
        int blockSkippedPerEmptyLayer = 0;
        if (prunedLayers > 0 && (west == null || east == null || north == null || south == null)) {
            for (int localZ = 0; localZ < 16; ++localZ) {
                for (int localX = 0; localX < 16; ++localX) {
                    if (scope == Scope.BORDER && localX != 0 && localX != 15 && localZ != 0 && localZ != 15) {
                        continue;
                    }
                    if ((localX == 0 && west == null) || (localX == 15 && east == null)
                            || (localZ == 0 && north == null) || (localZ == 15 && south == null)) {
                        ++blockSkippedPerEmptyLayer;
                    }
                }
            }
        }

        int[] positions = new int[Math.max(0, maxReported)];
        int reported = 0;
        int checked = 0;
        int skipped = 0;
        int violations = 0;

        for (int type = 0; type < 2; ++type) {
            boolean sky = type == 0;

            if (sky && !region.hasSkyLight()) {
                continue;
            }

            for (int y = 0; y < maxY; ++y) {
                for (int localZ = 0; localZ < 16; ++localZ) {
                    for (int localX = 0; localX < 16; ++localX) {
                        if (scope == Scope.BORDER && localX != 0 && localX != 15 && localZ != 0 && localZ != 15) {
                            continue;
                        }

                        int expected = expected(region, sky, chunkX, chunkZ, localX, y, localZ);

                        if (expected < 0) {
                            ++skipped;
                            continue;
                        }

                        ++checked;
                        if (column.stored(sky, localX, y, localZ) != expected) {
                            ++violations;
                            if (reported < positions.length) {
                                positions[reported++] = LightRules.packPosition(localX, y, localZ, sky);
                            }
                        }
                    }
                }
            }

            if (prunedLayers > 0) {
                if (sky) {
                    // For y >= maxY >= height, expected() returns MAX_LIGHT before checking lateral neighbours.
                    checked += colsInScope * prunedLayers;
                } else {
                    skipped += blockSkippedPerEmptyLayer * prunedLayers;
                    checked += (colsInScope - blockSkippedPerEmptyLayer) * prunedLayers;
                }
            }
        }

        return new Report(checked, skipped, violations, Arrays.copyOf(positions, reported));
    }

    private static int highestActiveSection(LightColumn col) {
        if (col == null) {
            return -1;
        }
        int highest = -1;
        for (int s = LightRules.SECTIONS - 1; s >= 0; --s) {
            if (col.cells(s) != null || col.storedBlock(s) != null || col.storedSky(s) != null) {
                highest = s;
                break;
            }
        }
        int[] hm = col.heightMap();
        int maxH = 0;
        for (int i = 0; i < 256; ++i) {
            if (hm[i] > maxH) {
                maxH = hm[i];
            }
        }
        if (maxH > 0) {
            int heightSection = (maxH - 1) >> 4;
            if (heightSection > highest) {
                highest = heightSection;
            }
        }
        return highest;
    }

    /**
     * The light the rule says this cell should have given its neighbours' stored light, or -1 when it cannot be decided
     * because a horizontal neighbour column is absent from the region (and the answer is not fixed by the cell itself).
     */
    public static int expected(LightRegion region, boolean sky, int chunkX, int chunkZ, int localX, int y, int localZ) {
        LightColumn column = region.column(chunkX, chunkZ);

        if (column == null) {
            throw new IllegalArgumentException("chunk " + chunkX + "," + chunkZ + " is not in the region");
        }

        if (sky && y >= column.height(localX, localZ)) {
            return LightRules.MAX_LIGHT;
        }

        byte cell = column.cell(localX, y, localZ);
        int cost = LightRules.cost(cell);

        if (cost >= LightRules.BLOCKED) {
            return 0;
        }

        int best = sky ? 0 : LightRules.emission(cell);
        // above y = 255 vanilla reads the world-edge default (15 sky / 0 block); below y = 0 it reads the cell itself
        int up = y + 1 > 255 ? (sky ? LightRules.MAX_LIGHT : 0) : column.stored(sky, localX, y + 1, localZ);
        int down = column.stored(sky, localX, y - 1 < 0 ? 0 : y - 1, localZ);

        best = Math.max(best, Math.max(up, down) - cost);

        boolean unknown = false;
        int neighbour;

        neighbour = lateral(region, sky, chunkX, chunkZ, localX + 1, y, localZ);
        unknown |= neighbour < 0;
        best = Math.max(best, neighbour - cost);
        neighbour = lateral(region, sky, chunkX, chunkZ, localX - 1, y, localZ);
        unknown |= neighbour < 0;
        best = Math.max(best, neighbour - cost);
        neighbour = lateral(region, sky, chunkX, chunkZ, localX, y, localZ + 1);
        unknown |= neighbour < 0;
        best = Math.max(best, neighbour - cost);
        neighbour = lateral(region, sky, chunkX, chunkZ, localX, y, localZ - 1);
        unknown |= neighbour < 0;
        best = Math.max(best, neighbour - cost);
        return unknown ? -1 : best;
    }

    /** Stored light of a horizontal neighbour (possibly in the next chunk), or -1 when that column is absent. */
    private static int lateral(LightRegion region, boolean sky, int chunkX, int chunkZ, int localX, int y, int localZ) {
        int dx = localX < 0 ? -1 : (localX > 15 ? 1 : 0);
        int dz = localZ < 0 ? -1 : (localZ > 15 ? 1 : 0);
        LightColumn column = region.column(chunkX + dx, chunkZ + dz);

        return column == null ? -1 : column.stored(sky, localX & 15, y, localZ & 15);
    }
}
