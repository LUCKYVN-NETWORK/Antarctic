package me.stella.antarctic.light;

/**
 * Height map derivation from packed cells. Vanilla defines a column's height as one above its highest cell with a
 * non-zero light opacity (0 for an all-transparent column); cells at or above it see the sky. A relight over blocks that
 * were written without maintaining the height map (bulk edits) must start from heights derived this way.
 */
public final class HeightMaps {

    private HeightMaps() {
    }

    /**
     * Derives all 256 heights, index {@code z << 4 | x}.
     *
     * @param cells the 16 sections of one column, null for a missing section
     */
    public static int[] compute(byte[][] cells) {
        int[] heights = new int[256];

        for (int localZ = 0; localZ < 16; ++localZ) {
            for (int localX = 0; localX < 16; ++localX) {
                heights[localZ << 4 | localX] = column(cells, localX, localZ);
            }
        }

        return heights;
    }

    /** Height of one column: highest cell with opacity &gt; 0, plus one; 0 when there is none. */
    public static int column(byte[][] cells, int localX, int localZ) {
        for (int section = LightRules.SECTIONS - 1; section >= 0; --section) {
            byte[] sectionCells = cells[section];

            if (sectionCells == null) {
                continue;
            }

            for (int localY = 15; localY >= 0; --localY) {
                if (LightRules.opacity(sectionCells[LightRules.cellIndex(localX, localY, localZ)]) != 0) {
                    return (section << 4) + localY + 1;
                }
            }
        }

        return 0;
    }
}
