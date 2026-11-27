package me.stella.antarctic.spatial;

/**
 * Reusable zero-allocation per-slice spatial density histogram (16x16x16 = 4096 cells).
 * Clears only touched indices on reset() so O(S) slice scans pay zero allocation and zero full-array clear cost.
 */
public final class CellDensityScratch {

    public static final int CELL_COUNT = 4096;

    private final short[] totalCounts = new short[CELL_COUNT];
    private final short[] fluidOrAirCounts = new short[CELL_COUNT];
    private final int[] touchedIndices = new int[CELL_COUNT];
    private int touchedCount;

    public static int packIndex(int localX, int localY, int localZ) {
        return ((localY & 15) << 8) | ((localZ & 15) << 4) | (localX & 15);
    }

    public void record(int idx, boolean inWaterOrAirborne) {
        short prev = this.totalCounts[idx];
        if (prev == 0) {
            this.touchedIndices[this.touchedCount++] = idx;
        }
        if (prev < Short.MAX_VALUE) {
            this.totalCounts[idx] = (short) (prev + 1);
        }
        if (inWaterOrAirborne) {
            short prevFluid = this.fluidOrAirCounts[idx];
            if (prevFluid < Short.MAX_VALUE) {
                this.fluidOrAirCounts[idx] = (short) (prevFluid + 1);
            }
        }
    }

    public short getTotalCount(int idx) {
        return this.totalCounts[idx];
    }

    public void decrementTotal(int idx) {
        if (this.totalCounts[idx] > 0) {
            this.totalCounts[idx]--;
        }
    }

    public short getFluidOrAirCount(int idx) {
        return this.fluidOrAirCounts[idx];
    }

    public int getTouchedCount() {
        return this.touchedCount;
    }

    public void reset() {
        for (int i = 0; i < this.touchedCount; ++i) {
            int idx = this.touchedIndices[i];
            this.totalCounts[idx] = 0;
            this.fluidOrAirCounts[idx] = 0;
        }
        this.touchedCount = 0;
    }
}
