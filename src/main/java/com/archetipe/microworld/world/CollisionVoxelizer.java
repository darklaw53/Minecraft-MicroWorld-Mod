package com.archetipe.microworld.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public final class CollisionVoxelizer {

    private static final double EPSILON = 1.0e-7;

    private CollisionVoxelizer() {
    }

    public static boolean[][][] create(
            LevelReader level,
            BlockPos pos,
            BlockState state,
            int scale
    ) {
        boolean[][][] occupied = new boolean[scale][scale][scale];

        VoxelShape shape = state.getCollisionShape(level, pos);

        if (shape.isEmpty()) {
            return occupied;
        }

        List<AABB> boxes = shape.toAabbs();

        for (int x = 0; x < scale; x++) {
            double minX = (double) x / scale;
            double maxX = (double) (x + 1) / scale;

            for (int y = 0; y < scale; y++) {
                double minY = (double) y / scale;
                double maxY = (double) (y + 1) / scale;

                for (int z = 0; z < scale; z++) {
                    double minZ = (double) z / scale;
                    double maxZ = (double) (z + 1) / scale;

                    if (intersectsAny(
                            boxes,
                            minX,
                            minY,
                            minZ,
                            maxX,
                            maxY,
                            maxZ
                    )) {
                        occupied[x][y][z] = true;
                    }
                }
            }
        }

        return occupied;
    }

    private static boolean intersectsAny(
            List<AABB> boxes,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ
    ) {
        for (AABB box : boxes) {
            if (overlaps(
                    minX,
                    minY,
                    minZ,
                    maxX,
                    maxY,
                    maxZ,
                    box
            )) {
                return true;
            }
        }

        return false;
    }

    private static boolean overlaps(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            AABB box
    ) {
        return maxX > box.minX + EPSILON
                && minX < box.maxX - EPSILON
                && maxY > box.minY + EPSILON
                && minY < box.maxY - EPSILON
                && maxZ > box.minZ + EPSILON
                && minZ < box.maxZ - EPSILON;
    }
}