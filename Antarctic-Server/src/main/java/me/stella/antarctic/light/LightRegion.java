package me.stella.antarctic.light;

/**
 * A rectangle of chunk columns handed to the engine. Each column is a <em>target</em> (its light is computed or
 * checked), a <em>ring</em> column (its stored light is a fixed input that feeds the targets) or absent (not loaded: it
 * contributes nothing, and cells that need it cannot be judged by the checker).
 *
 * <p>Region block coordinates are {@code (chunkX - minChunkX) * 16 + localX}; a region may span at most 256 chunks per axis.
 */
public final class LightRegion {

    private final int minChunkX;
    private final int minChunkZ;
    private final int width;
    private final int depth;
    private final boolean hasSkyLight;
    private final LightColumn[] columns;
    private final boolean[] targets;

    public LightRegion(int minChunkX, int minChunkZ, int width, int depth, boolean hasSkyLight) {
        if (width < 1 || depth < 1 || width > 256 || depth > 256) {
            throw new IllegalArgumentException("region must be 1..256 chunks per axis: " + width + "x" + depth);
        }

        this.minChunkX = minChunkX;
        this.minChunkZ = minChunkZ;
        this.width = width;
        this.depth = depth;
        this.hasSkyLight = hasSkyLight;
        this.columns = new LightColumn[width * depth];
        this.targets = new boolean[width * depth];
    }

    /** Puts a column into the region; {@code target} true computes/checks it, false makes it a fixed ring input. */
    public LightRegion put(int chunkX, int chunkZ, LightColumn column, boolean target) {
        int index = this.index(chunkX, chunkZ);

        if (index < 0) {
            throw new IllegalArgumentException("chunk " + chunkX + "," + chunkZ + " is outside the region");
        }

        this.columns[index] = column;
        this.targets[index] = column != null && target;
        return this;
    }

    /** Index of a chunk in this region, or -1 when it is outside. */
    public int index(int chunkX, int chunkZ) {
        int dx = chunkX - this.minChunkX;
        int dz = chunkZ - this.minChunkZ;

        return dx < 0 || dz < 0 || dx >= this.width || dz >= this.depth ? -1 : dz * this.width + dx;
    }

    public int minChunkX() {
        return this.minChunkX;
    }

    public int minChunkZ() {
        return this.minChunkZ;
    }

    public int width() {
        return this.width;
    }

    public int depth() {
        return this.depth;
    }

    public boolean hasSkyLight() {
        return this.hasSkyLight;
    }

    public int columnCount() {
        return this.columns.length;
    }

    public LightColumn column(int index) {
        return this.columns[index];
    }

    public boolean isTarget(int index) {
        return this.targets[index];
    }

    public int chunkX(int index) {
        return this.minChunkX + index % this.width;
    }

    public int chunkZ(int index) {
        return this.minChunkZ + index / this.width;
    }

    public LightColumn column(int chunkX, int chunkZ) {
        int index = this.index(chunkX, chunkZ);

        return index < 0 ? null : this.columns[index];
    }

    public boolean isTarget(int chunkX, int chunkZ) {
        int index = this.index(chunkX, chunkZ);

        return index >= 0 && this.targets[index];
    }
}
