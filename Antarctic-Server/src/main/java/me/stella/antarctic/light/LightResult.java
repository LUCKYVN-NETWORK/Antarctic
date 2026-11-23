package me.stella.antarctic.light;

/**
 * Output of {@link LightEngine#relight}: for every target column and section, the computed light as vanilla-layout nibble
 * arrays. A {@code null} array means no array is needed: either the column is not a target, or the section does not exist
 * and the implicit value (sky 15 at or above the height, otherwise 0; block 0) is already correct. A non-null array for a
 * section that did not exist means the section must be created to hold light that spilled into it.
 */
public final class LightResult {

    private final LightRegion region;
    private final byte[][][] sky;
    private final byte[][][] block;
    private long cellsRaised;

    LightResult(LightRegion region) {
        this.region = region;
        this.sky = new byte[region.columnCount()][][];
        this.block = new byte[region.columnCount()][][];
        for (int i = 0; i < region.columnCount(); ++i) {
            if (region.isTarget(i)) {
                this.sky[i] = new byte[LightRules.SECTIONS][];
                this.block[i] = new byte[LightRules.SECTIONS][];
            }
        }
    }

    void put(boolean skyLight, int columnIndex, int section, byte[] nibbles) {
        (skyLight ? this.sky : this.block)[columnIndex][section] = nibbles;
    }

    void addRaised(long cells) {
        this.cellsRaised += cells;
    }

    public byte[] sky(int chunkX, int chunkZ, int section) {
        int index = this.region.index(chunkX, chunkZ);

        return index < 0 || this.sky[index] == null ? null : this.sky[index][section];
    }

    public byte[] block(int chunkX, int chunkZ, int section) {
        int index = this.region.index(chunkX, chunkZ);

        return index < 0 || this.block[index] == null ? null : this.block[index][section];
    }

    public byte[] sky(int columnIndex, int section) {
        return this.sky[columnIndex] == null ? null : this.sky[columnIndex][section];
    }

    public byte[] block(int columnIndex, int section) {
        return this.block[columnIndex] == null ? null : this.block[columnIndex][section];
    }

    /** Number of cell updates the flood fill performed (a cost measure for tests and benchmarks). */
    public long cellsRaised() {
        return this.cellsRaised;
    }

    /**
     * Writes this result into the region's target columns as their stored light, simulating an apply. Used by tests and by
     * the post-apply verification to check a result without touching live chunks.
     */
    public void storeInto(LightRegion target) {
        if (target != this.region) {
            throw new IllegalArgumentException("result belongs to another region");
        }

        for (int i = 0; i < this.region.columnCount(); ++i) {
            if (!this.region.isTarget(i)) {
                continue;
            }

            LightColumn column = this.region.column(i);

            for (int s = 0; s < LightRules.SECTIONS; ++s) {
                column.setStored(s, this.sky[i][s], this.block[i][s]);
            }
        }
    }
}
