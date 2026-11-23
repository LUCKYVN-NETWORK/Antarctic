package me.stella.antarctic.light;

/**
 * Constants, the packed per-cell encoding and nibble helpers shared by the light engine and the consistency checker.
 * Nothing in this package references Minecraft classes, so it can run on worker threads and be unit-tested alone.
 *
 * <p>A cell is one byte: the low nibble is the block's light opacity clamped to 0..15 and the high nibble is its light
 * emission (0..15). Vanilla 1.12.2's per-cell light rule (World.a(BlockPosition, EnumSkyBlock)) only depends on those
 * two numbers, so a table of this byte per block state is all a snapshot needs.
 *
 * <p>The rule, for a cell with packed value {@code v} and the light {@code L} stored in its six neighbours:
 * <pre>
 *   sky light, cell can see the sky (y &gt;= height of its column)  -&gt; 15
 *   cost c = opacity; if c &gt;= 15 and emission &gt; 0 then c = 1; if c &lt; 1 then c = 1
 *   c &gt;= 15                                                    -&gt; 0
 *   otherwise  max(sky ? 0 : emission, max over neighbours of L(n) - c)
 * </pre>
 * Neighbours above y = 255 read as 15 for sky light and 0 for block light; below y = 0 the cell reads itself (vanilla
 * clamps y to 0), which can never change a consistent state because it only subtracts at least 1.
 *
 * <p>Antarctic - rebuilt from the design of Starlight (Spottedleaf / PaperMC; LGPL-3.0): light is computed as the fixed
 * point of local rules. No code is copied.
 */
public final class LightRules {

    public static final int MAX_LIGHT = 15;
    public static final int SECTIONS = 16;
    public static final int SECTION_CELLS = 4096;
    public static final int NIBBLE_BYTES = 2048;
    public static final int WORLD_HEIGHT = 256;
    /** Cost value meaning "light cannot enter this cell". */
    public static final int BLOCKED = 15;

    /** Effective cost of entering a cell, indexed by the packed cell byte (0..255). */
    private static final byte[] COST = new byte[256];

    static {
        for (int v = 0; v < 256; ++v) {
            int opacity = v & 15;
            int emission = v >>> 4;
            int cost = opacity;

            if (cost >= 15 && emission > 0) {
                cost = 1;
            }

            if (cost < 1) {
                cost = 1;
            }

            COST[v] = (byte) cost;
        }
    }

    private LightRules() {
    }

    /** Packs a block state's opacity (any int, clamped to 0..15) and emission (clamped to 0..15). */
    public static byte pack(int opacity, int emission) {
        int o = opacity < 0 ? 0 : Math.min(opacity, 15);
        int e = emission < 0 ? 0 : Math.min(emission, 15);

        return (byte) (e << 4 | o);
    }

    public static int opacity(byte cell) {
        return cell & 15;
    }

    public static int emission(byte cell) {
        return (cell & 0xFF) >>> 4;
    }

    /** Cost of entering the cell, 1..14, or {@link #BLOCKED} (15) when light cannot enter it. */
    public static int cost(byte cell) {
        return COST[cell & 0xFF];
    }

    /** Index of a cell inside a 16x16x16 section; identical to ChunkSection / NibbleArray (y &lt;&lt; 8 | z &lt;&lt; 4 | x). */
    public static int cellIndex(int localX, int localY, int localZ) {
        return localY << 8 | localZ << 4 | localX;
    }

    /** Reads the nibble at {@code index} of a vanilla-layout nibble array (2048 bytes, low nibble first). */
    public static int getNibble(byte[] nibbles, int index) {
        return nibbles[index >> 1] >> ((index & 1) << 2) & 15;
    }

    public static void setNibble(byte[] nibbles, int index, int value) {
        int shift = (index & 1) << 2;

        nibbles[index >> 1] = (byte) (nibbles[index >> 1] & ~(15 << shift) | (value & 15) << shift);
    }

    /**
     * Packs a chunk-local position the same way as {@code me.stella.antarctic.collection.PendingLightRegistry.pack}, so a
     * checker report can be fed straight into the deferred-light registry. A test pins both encodings together.
     */
    public static int packPosition(int localX, int y, int localZ, boolean sky) {
        return (sky ? 1 << 16 : 0) | (y & 255) << 8 | (localZ & 15) << 4 | (localX & 15);
    }

    public static int positionX(int packed) {
        return packed & 15;
    }

    public static int positionZ(int packed) {
        return packed >> 4 & 15;
    }

    public static int positionY(int packed) {
        return packed >> 8 & 255;
    }

    public static boolean positionIsSky(int packed) {
        return (packed >> 16 & 1) != 0;
    }
}
