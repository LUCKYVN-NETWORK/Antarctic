package me.stella.antarctic.math;

import java.util.Random;

/**
 * Non-atomic, bit-exact drop-in replacement for {@link java.util.Random} intended
 * strictly for single-threaded world generation paths.
 */
public final class FastRandom extends Random {

    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long ADDEND = 0xBL;
    private static final long MASK = (1L << 48) - 1L;
    private static final double DOUBLE_UNIT = 0x1.0p-53;
    private static final float FLOAT_UNIT = 0x1.0p-24f;

    private long seed;
    private double nextNextGaussian;
    private boolean haveNextNextGaussian;

    public FastRandom() {
        super();
        this.setSeed(super.nextLong());
    }

    public FastRandom(long seed) {
        super(0L);
        this.setSeed(seed);
    }

    @Override
    public void setSeed(long seed) {
        this.seed = (seed ^ MULTIPLIER) & MASK;
        this.haveNextNextGaussian = false;
    }

    @Override
    protected int next(int bits) {
        long s = (this.seed * MULTIPLIER + ADDEND) & MASK;
        this.seed = s;
        return (int) (s >>> (48 - bits));
    }

    @Override
    public int nextInt() {
        return this.next(32);
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        int r = this.next(31);
        int m = bound - 1;
        if ((bound & m) == 0) {
            r = (int) ((bound * (long) r) >> 31);
        } else {
            for (int u = r; u - (r = u % bound) + m < 0; u = this.next(31)) {
                // rejection sampling for modulo bias parity
            }
        }
        return r;
    }

    @Override
    public long nextLong() {
        return ((long) this.next(32) << 32) + this.next(32);
    }

    @Override
    public boolean nextBoolean() {
        return this.next(1) != 0;
    }

    @Override
    public float nextFloat() {
        return this.next(24) * FLOAT_UNIT;
    }

    @Override
    public double nextDouble() {
        return (((long) this.next(26) << 27) + this.next(27)) * DOUBLE_UNIT;
    }

    @Override
    public double nextGaussian() {
        if (this.haveNextNextGaussian) {
            this.haveNextNextGaussian = false;
            return this.nextNextGaussian;
        }
        double v1, v2, s;
        do {
            v1 = 2.0D * this.nextDouble() - 1.0D;
            v2 = 2.0D * this.nextDouble() - 1.0D;
            s = v1 * v1 + v2 * v2;
        } while (s >= 1.0D || s == 0.0D);
        double multiplier = StrictMath.sqrt(-2.0D * StrictMath.log(s) / s);
        this.nextNextGaussian = v2 * multiplier;
        this.haveNextNextGaussian = true;
        return v1 * multiplier;
    }
}
