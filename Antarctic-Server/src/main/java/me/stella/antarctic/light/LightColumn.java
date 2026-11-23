package me.stella.antarctic.light;

/**
 * Plain data of one chunk column for the light engine: per-section packed cell bytes, the height map and, optionally,
 * the light currently stored in each section (vanilla nibble layout).
 *
 * <p>A {@code null} entry in the cells means the section does not exist (vanilla's empty section {@code Chunk.a}); it
 * behaves as all air. A {@code null} stored array means "implicit": sky light reads 15 at or above the column's height
 * and 0 below it, block light reads 0 (vanilla's {@code Chunk.getBrightness} for a missing section).
 *
 * <p>Arrays are held by reference and are never copied or modified by the engine; whoever builds the column (the snapshot
 * on the main thread) must not change them while an engine call is running.
 */
public final class LightColumn {

    private final int[] heightMap;
    private final byte[][] cells = new byte[LightRules.SECTIONS][];
    private final byte[][] sky = new byte[LightRules.SECTIONS][];
    private final byte[][] block = new byte[LightRules.SECTIONS][];

    /** @param heightMap 256 entries, index {@code z << 4 | x}: the lowest y from which a cell can see the sky (0..256) */
    public LightColumn(int[] heightMap) {
        if (heightMap.length != 256) {
            throw new IllegalArgumentException("height map must have 256 entries, not " + heightMap.length);
        }

        this.heightMap = heightMap;
    }

    /** Sets the packed cells of an existing section (4096 bytes, index y &lt;&lt; 8 | z &lt;&lt; 4 | x). */
    public LightColumn setCells(int section, byte[] packedCells) {
        if (packedCells != null && packedCells.length != LightRules.SECTION_CELLS) {
            throw new IllegalArgumentException("cells must have 4096 entries, not " + packedCells.length);
        }

        this.cells[section] = packedCells;
        return this;
    }

    /** Sets the stored light of a section; either array may be null for "implicit". */
    public LightColumn setStored(int section, byte[] skyNibbles, byte[] blockNibbles) {
        checkNibbles(skyNibbles);
        checkNibbles(blockNibbles);
        this.sky[section] = skyNibbles;
        this.block[section] = blockNibbles;
        return this;
    }

    private static void checkNibbles(byte[] nibbles) {
        if (nibbles != null && nibbles.length != LightRules.NIBBLE_BYTES) {
            throw new IllegalArgumentException("nibble arrays must have 2048 bytes, not " + nibbles.length);
        }
    }

    public int height(int localX, int localZ) {
        return this.heightMap[localZ << 4 | localX];
    }

    public int[] heightMap() {
        return this.heightMap;
    }

    public byte[] cells(int section) {
        return this.cells[section];
    }

    public byte[] storedSky(int section) {
        return this.sky[section];
    }

    public byte[] storedBlock(int section) {
        return this.block[section];
    }

    /** The packed cell at a column-local position; a missing section reads as air (0). */
    public byte cell(int localX, int y, int localZ) {
        byte[] section = this.cells[y >> 4];

        return section == null ? 0 : section[LightRules.cellIndex(localX, y & 15, localZ)];
    }

    /** The stored light at a column-local position, or the implicit value when the section has no stored array. */
    public int stored(boolean skyLight, int localX, int y, int localZ) {
        byte[] nibbles = skyLight ? this.sky[y >> 4] : this.block[y >> 4];

        if (nibbles != null) {
            return LightRules.getNibble(nibbles, LightRules.cellIndex(localX, y & 15, localZ));
        }

        return skyLight && y >= this.height(localX, localZ) ? LightRules.MAX_LIGHT : 0;
    }
}
