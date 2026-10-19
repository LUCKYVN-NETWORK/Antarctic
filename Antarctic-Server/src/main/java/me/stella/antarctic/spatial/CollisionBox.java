package me.stella.antarctic.spatial;

import net.minecraft.server.AxisAlignedBB;

/**
 * Low-level allocation-free collision and bounding box structure inferred from Lithium/Leaf.
 * Performs ray/box clipping and axis collision offset calculations without allocating
 * immutable AxisAlignedBB heap objects during tick sweeps.
 */
public final class CollisionBox {

    public double minX;
    public double minY;
    public double minZ;
    public double maxX;
    public double maxY;
    public double maxZ;

    public CollisionBox() {
    }

    public CollisionBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        set(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public CollisionBox set(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        return this;
    }

    public CollisionBox copyFrom(AxisAlignedBB bb) {
        this.minX = bb.a;
        this.minY = bb.b;
        this.minZ = bb.c;
        this.maxX = bb.d;
        this.maxY = bb.e;
        this.maxZ = bb.f;
        return this;
    }

    public CollisionBox copyFrom(CollisionBox other) {
        this.minX = other.minX;
        this.minY = other.minY;
        this.minZ = other.minZ;
        this.maxX = other.maxX;
        this.maxY = other.maxY;
        this.maxZ = other.maxZ;
        return this;
    }

    public boolean intersects(double otherMinX, double otherMinY, double otherMinZ,
                              double otherMaxX, double otherMaxY, double otherMaxZ) {
        return this.minX < otherMaxX && this.maxX > otherMinX
            && this.minY < otherMaxY && this.maxY > otherMinY
            && this.minZ < otherMaxZ && this.maxZ > otherMinZ;
    }

    public boolean intersects(CollisionBox other) {
        return intersects(other.minX, other.minY, other.minZ, other.maxX, other.maxY, other.maxZ);
    }

    public boolean intersects(AxisAlignedBB bb) {
        return intersects(bb.a, bb.b, bb.c, bb.d, bb.e, bb.f);
    }

    /**
     * Calculates the collision offset along the X axis between this box and a target bounding box.
     */
    public double calculateXOffset(double targetMinX, double targetMinY, double targetMinZ,
                                   double targetMaxX, double targetMaxY, double targetMaxZ,
                                   double offsetX) {
        if (targetMaxY > this.minY && targetMinY < this.maxY && targetMaxZ > this.minZ && targetMinZ < this.maxZ) {
            if (offsetX > 0.0D && targetMaxX <= this.minX) {
                double delta = this.minX - targetMaxX;
                if (delta < offsetX) {
                    offsetX = delta;
                }
            } else if (offsetX < 0.0D && targetMinX >= this.maxX) {
                double delta = this.maxX - targetMinX;
                if (delta > offsetX) {
                    offsetX = delta;
                }
            }
        }
        return offsetX;
    }

    /**
     * Calculates the collision offset along the Y axis between this box and a target bounding box.
     */
    public double calculateYOffset(double targetMinX, double targetMinY, double targetMinZ,
                                   double targetMaxX, double targetMaxY, double targetMaxZ,
                                   double offsetY) {
        if (targetMaxX > this.minX && targetMinX < this.maxX && targetMaxZ > this.minZ && targetMinZ < this.maxZ) {
            if (offsetY > 0.0D && targetMaxY <= this.minY) {
                double delta = this.minY - targetMaxY;
                if (delta < offsetY) {
                    offsetY = delta;
                }
            } else if (offsetY < 0.0D && targetMinY >= this.maxY) {
                double delta = this.maxY - targetMinY;
                if (delta > offsetY) {
                    offsetY = delta;
                }
            }
        }
        return offsetY;
    }

    /**
     * Calculates the collision offset along the Z axis between this box and a target bounding box.
     */
    public double calculateZOffset(double targetMinX, double targetMinY, double targetMinZ,
                                   double targetMaxX, double targetMaxY, double targetMaxZ,
                                   double offsetZ) {
        if (targetMaxX > this.minX && targetMinX < this.maxX && targetMaxY > this.minY && targetMinY < this.maxY) {
            if (offsetZ > 0.0D && targetMaxZ <= this.minZ) {
                double delta = this.minZ - targetMaxZ;
                if (delta < offsetZ) {
                    offsetZ = delta;
                }
            } else if (offsetZ < 0.0D && targetMinZ >= this.maxZ) {
                double delta = this.maxZ - targetMinZ;
                if (delta > offsetZ) {
                    offsetZ = delta;
                }
            }
        }
        return offsetZ;
    }

    public AxisAlignedBB toAxisAlignedBB() {
        return new AxisAlignedBB(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }
}
