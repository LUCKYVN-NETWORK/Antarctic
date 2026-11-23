package me.stella.antarctic.spatial;

import java.util.List;
import net.minecraft.server.AxisAlignedBB;
import net.minecraft.server.Block;
import net.minecraft.server.BlockPosition;
import net.minecraft.server.Blocks;
import net.minecraft.server.Entity;
import net.minecraft.server.IBlockAccess;
import net.minecraft.server.IBlockData;
import net.minecraft.server.World;

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

    /**
     * Whether a block class replaces any of the three shape hooks that feed collision: the collision box
     * ({@code Block.a(IBlockData, IBlockAccess, BlockPosition)}, 1.12.2 getCollisionBoundingBox), the selection box
     * ({@code Block.b(...)}, getBoundingBox) and the box-list hook ({@code addCollisionBoxToList}). The occluding
     * and full-cube flags do not prove a unit collision box: soul sand is an opaque cube whose collision box is 0.875 tall.
     */
    private static final ClassValue<Boolean> CUSTOM_SHAPE = new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null && c != Block.class; c = c.getSuperclass()) {
                if (declares(c, "a", IBlockData.class, IBlockAccess.class, BlockPosition.class)
                        || declares(c, "b", IBlockData.class, IBlockAccess.class, BlockPosition.class)
                        || declares(c, "a", IBlockData.class, World.class, BlockPosition.class, AxisAlignedBB.class,
                                List.class, Entity.class, boolean.class)) {
                    return Boolean.TRUE;
                }
            }
            return Boolean.FALSE;
        }
    };

    private static boolean declares(Class<?> type, String name, Class<?>... parameters) {
        try {
            type.getDeclaredMethod(name, parameters);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private FastVoxelCache() {
    }

    /**
     * Classifies an IBlockData instance into an optimization category using the per-state cache on
     * {@link net.minecraft.server.BlockDataAbstract} to eliminate repeated virtual dispatch and {@link ClassValue} probes.
     */
    public static ShapeType classify(IBlockData data) {
        if (data == null) {
            return ShapeType.EMPTY;
        }
        if (data instanceof net.minecraft.server.BlockDataAbstract) {
            return ((net.minecraft.server.BlockDataAbstract) data).antarcticShapeType();
        }
        return computeShapeType(data);
    }

    /**
     * Uncached classification used to populate {@link net.minecraft.server.BlockDataAbstract#antarcticShapeType()} once per state.
     */
    public static ShapeType computeShapeType(IBlockData data) {
        if (data == null) {
            return ShapeType.EMPTY;
        }
        Block block = data.getBlock();
        if (block == Blocks.AIR) {
            return ShapeType.EMPTY;
        }
        // Material flags do not prove collision absence: carpet and lily pads still have collision boxes.
        if (block.isOccluding(data) && block.l(data) && !CUSTOM_SHAPE.get(block.getClass())) {
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
