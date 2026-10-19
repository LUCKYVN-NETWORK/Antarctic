package me.stella.antarctic.spatial;

import net.minecraft.server.Block;
import net.minecraft.server.Blocks;
import net.minecraft.server.IBlockData;
import net.minecraft.server.Material;

/**
 * Fast collision shape classification inferred from Lithium / Leaf.
 * Categorizes blocks into non-colliding (air/passable), full-cube, or complex shapes
 * to bypass heavy NMS virtual dispatch during collision queries.
 */
public final class FastVoxelCache {

    public enum ShapeType {
        EMPTY,
        FULL_CUBE,
        CUSTOM
    }

    private FastVoxelCache() {
    }

    /**
     * Classifies an IBlockData instance into an optimization category.
     */
    public static ShapeType classify(IBlockData data) {
        if (data == null) {
            return ShapeType.EMPTY;
        }
        Block block = data.getBlock();
        if (block == Blocks.AIR) {
            return ShapeType.EMPTY;
        }
        Material material = data.getMaterial();
        if (material == Material.AIR || !material.isSolid()) {
            if (!material.isLiquid() && !material.isBuildable()) {
                return ShapeType.EMPTY;
            }
        }
        if (block.isOccluding(data) && block.l(data)) {
            return ShapeType.FULL_CUBE;
        }
        return ShapeType.CUSTOM;
    }

    /**
     * Fast check if a block is guaranteed to have no collision box.
     */
    public static boolean isEmpty(IBlockData data) {
        return classify(data) == ShapeType.EMPTY;
    }

    /**
     * Fast check if a block is a standard 1x1x1 solid cube.
     */
    public static boolean isFullCube(IBlockData data) {
        return classify(data) == ShapeType.FULL_CUBE;
    }
}
