package me.stella.antarctic.math;

/**
 * High-performance bitwise and arithmetic operations inferred from Leaf/Lithium.
 * Eliminates boxing, branching, and intermediate allocations in hot physics/tick paths.
 */
public final class FastMath {

    private FastMath() {
    }

    /**
     * Packs chunk coordinates (chunkX, chunkZ) into a single 64-bit primitive long.
     */
    public static long packChunkPos(int x, int z) {
        return (((long) x) << 32) | (((long) z) & 0xFFFFFFFFL);
    }

    public static int unpackChunkX(long key) {
        return (int) (key >> 32);
    }

    public static int unpackChunkZ(long key) {
        return (int) key;
    }

    /**
     * Packs block coordinates into a single 64-bit long matching NMS 1.12.2 BlockPosition packing:
     * 26 bits for X, 12 bits for Y, 26 bits for Z.
     */
    public static long packBlockPos(int x, int y, int z) {
        return (((long) x & 0x3FFFFFFL) << 38) | (((long) y & 0xFFFL) << 26) | ((long) z & 0x3FFFFFFL);
    }

    public static int unpackBlockX(long key) {
        return (int) (key >> 38);
    }

    public static int unpackBlockY(long key) {
        return (int) (key << 26 >> 52);
    }

    public static int unpackBlockZ(long key) {
        return (int) (key << 38 >> 38);
    }

    /**
     * Fast branchless floor for double coordinates.
     */
    public static int floor(double d) {
        int i = (int) d;
        return d < (double) i ? i - 1 : i;
    }

    /**
     * Fast branchless ceil for double coordinates.
     */
    public static int ceil(double d) {
        int i = (int) d;
        return d > (double) i ? i + 1 : i;
    }

    /**
     * Fast primitive clamp.
     */
    public static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    /**
     * Euclidean squared distance between two 3D points.
     */
    public static double distanceSq(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Check if two points are within a given radius without computing square roots.
     */
    public static boolean isWithinDistance(double x1, double y1, double z1, double x2, double y2, double z2, double radius) {
        return distanceSq(x1, y1, z1, x2, y2, z2) <= (radius * radius);
    }

    /**
     * Fast Chebyshev distance (maximum coordinate delta) useful for bounding box and chunk reach checks.
     */
    public static double chebyshevDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = Math.abs(x1 - x2);
        double dy = Math.abs(y1 - y2);
        double dz = Math.abs(z1 - z2);
        return Math.max(dx, Math.max(dy, dz));
    }
}
