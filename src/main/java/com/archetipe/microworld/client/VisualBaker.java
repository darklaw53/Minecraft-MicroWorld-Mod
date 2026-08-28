package com.archetipe.microworld.client;

import com.archetipe.microworld.Microworld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class VisualBaker {

    // Public so callers (e.g. MicroWorldBakedModel, BlockPixelSampler) can
    // bounds-check local voxel coordinates before indexing into a VisualData
    // array.
    public static final int SCALE = 16;

    private static final float EPSILON = 1e-4f;

    private static final Map<BlockState, VisualData> CACHE = new ConcurrentHashMap<>();

    private VisualBaker() {}

    public static VisualData getVisualData(BlockState state) {
        return CACHE.computeIfAbsent(state, VisualBaker::bake);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static VisualData bake(BlockState state) {
        long start = System.nanoTime();
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getBlockRenderer().getBlockModel(state);

        RandomSource random = RandomSource.create(42L);
        List<BakedQuad> allQuads = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            allQuads.addAll(model.getQuads(state, dir, random));
        }
        allQuads.addAll(model.getQuads(state, null, random));

        boolean isAnvil = state.toString().contains("anvil");
        if (isAnvil) {
            Microworld.LOGGER.info("VisualBaker: Anvil block detected, total quads = {}", allQuads.size());
        }

        boolean[][][] occupied = new boolean[SCALE][SCALE][SCALE];
        Direction[][][] faceDir = new Direction[SCALE][SCALE][SCALE];
        int[][][] pixelX = new int[SCALE][SCALE][SCALE];
        int[][][] pixelY = new int[SCALE][SCALE][SCALE];
        TextureAtlasSprite[][][] sprite = new TextureAtlasSprite[SCALE][SCALE][SCALE];
        int[][][] tintIndex = new int[SCALE][SCALE][SCALE];

        // Determine coordinate scale (0-16 or 0-1)
        boolean scale16 = false;
        for (BakedQuad q : allQuads) {
            int[] verts = q.getVertices();
            for (int i = 0; i < 4; i++) {
                float x = Float.intBitsToFloat(verts[i * 8]);
                float y = Float.intBitsToFloat(verts[i * 8 + 1]);
                float z = Float.intBitsToFloat(verts[i * 8 + 2]);
                if (x > 1.1f || y > 1.1f || z > 1.1f) {
                    scale16 = true;
                    break;
                }
            }
            if (scale16) break;
        }
        if (isAnvil) {
            Microworld.LOGGER.info("VisualBaker: Anvil scale16 = {}", scale16);
        }

        for (BakedQuad quad : allQuads) {
            Direction face = quad.getDirection();
            TextureAtlasSprite spr = quad.getSprite();
            if (spr == null) continue;

            int[] verts = quad.getVertices();
            float[][] pos = new float[4][3];
            float[][] uv = new float[4][2];
            for (int i = 0; i < 4; i++) {
                int base = i * 8;
                pos[i][0] = Float.intBitsToFloat(verts[base]);
                pos[i][1] = Float.intBitsToFloat(verts[base + 1]);
                pos[i][2] = Float.intBitsToFloat(verts[base + 2]);
                uv[i][0] = Float.intBitsToFloat(verts[base + 4]);
                uv[i][1] = Float.intBitsToFloat(verts[base + 5]);
            }

            // Scale if needed
            if (!scale16) {
                for (int i = 0; i < 4; i++) {
                    pos[i][0] *= SCALE;
                    pos[i][1] *= SCALE;
                    pos[i][2] *= SCALE;
                }
            }

            // Compute bounding box of the quad
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
            float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (int i = 0; i < 4; i++) {
                minX = Math.min(minX, pos[i][0]);
                maxX = Math.max(maxX, pos[i][0]);
                minY = Math.min(minY, pos[i][1]);
                maxY = Math.max(maxY, pos[i][1]);
                minZ = Math.min(minZ, pos[i][2]);
                maxZ = Math.max(maxZ, pos[i][2]);
            }

            // Clamp to [0, SCALE]
            minX = Math.max(0, minX);
            maxX = Math.min(SCALE, maxX);
            minY = Math.max(0, minY);
            maxY = Math.min(SCALE, maxY);
            minZ = Math.max(0, minZ);
            maxZ = Math.min(SCALE, maxZ);

            // ---- Convert the continuous [min, max) bounding box into an
            // inclusive voxel index range [i0, i1] for each axis. ----
            //
            // For an axis where the quad has real extent (a proper range,
            // e.g. the 4-pixel width of a fence post's cross-section), the
            // standard closed-open -> inclusive conversion is:
            //   i0 = floor(min)         (first voxel the range enters)
            //   i1 = floor(max - eps)   (last voxel before the range exits)
            // A small epsilon guards against float error putting a value that
            // should be exactly on an integer boundary just below it.
            //
            // But most quads are completely FLAT along the axis perpendicular
            // to their own face (min == max on that one axis, e.g. minZ ==
            // maxZ for a north/south-facing quad sitting right at the block's
            // surface). Applying the two-sided formula above to a degenerate
            // range fails: floor(v + eps) and floor(v - eps) land on
            // *different* voxel indices, producing an empty range and
            // dropping that face's voxels entirely. For that axis we instead
            // need to know which side of the box this flat quad represents --
            // NORTH/WEST/DOWN sit at the box's minimum boundary on their
            // perpendicular axis, SOUTH/EAST/UP sit at the maximum -- and bias
            // the epsilon accordingly so it always resolves to the one voxel
            // just inside the shape.
            boolean xPerp = face == Direction.WEST || face == Direction.EAST;
            boolean yPerp = face == Direction.DOWN || face == Direction.UP;
            boolean zPerp = face == Direction.NORTH || face == Direction.SOUTH;

            int x0, x1, y0, y1, z0, z1;

            if (xPerp) {
                boolean minFace = face == Direction.WEST;
                int v = minFace ? floorMin(minX) : floorMax(maxX);
                x0 = v;
                x1 = v;
            } else {
                x0 = floorMin(minX);
                x1 = floorMax(maxX);
            }

            if (yPerp) {
                boolean minFace = face == Direction.DOWN;
                int v = minFace ? floorMin(minY) : floorMax(maxY);
                y0 = v;
                y1 = v;
            } else {
                y0 = floorMin(minY);
                y1 = floorMax(maxY);
            }

            if (zPerp) {
                boolean minFace = face == Direction.NORTH;
                int v = minFace ? floorMin(minZ) : floorMax(maxZ);
                z0 = v;
                z1 = v;
            } else {
                z0 = floorMin(minZ);
                z1 = floorMax(maxZ);
            }

            x0 = Math.max(0, Math.min(SCALE - 1, x0));
            x1 = Math.max(0, Math.min(SCALE - 1, x1));
            y0 = Math.max(0, Math.min(SCALE - 1, y0));
            y1 = Math.max(0, Math.min(SCALE - 1, y1));
            z0 = Math.max(0, Math.min(SCALE - 1, z0));
            z1 = Math.max(0, Math.min(SCALE - 1, z1));

            if (x0 > x1 || y0 > y1 || z0 > z1) continue;

            // Mark all voxels inside the bounding box
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        // Voxel centre
                        float cx = x + 0.5f;
                        float cy = y + 0.5f;
                        float cz = z + 0.5f;

                        // Interpolate UV
                        float u = interpolateUV(pos, uv, face, cx, cy, cz, 0);
                        float v = interpolateUV(pos, uv, face, cx, cy, cz, 1);

                        float u0 = spr.getU0();
                        float u1 = spr.getU1();
                        float v0 = spr.getV0();
                        float v1 = spr.getV1();
                        float width = u1 - u0;
                        float height = v1 - v0;
                        if (width <= 0 || height <= 0) continue;

                        int px = (int) Math.round((u - u0) / width * (SCALE - 1));
                        int py = (int) Math.round((v - v0) / height * (SCALE - 1));
                        px = Math.max(0, Math.min(SCALE - 1, px));
                        py = Math.max(0, Math.min(SCALE - 1, py));

                        if (!occupied[x][y][z]) {
                            occupied[x][y][z] = true;
                            faceDir[x][y][z] = face;
                            pixelX[x][y][z] = px;
                            pixelY[x][y][z] = py;
                            sprite[x][y][z] = spr;
                            tintIndex[x][y][z] = quad.getTintIndex();
                        }
                    }
                }
            }
        }

        // ---- Fill fully-enclosed interior voxels ----
        // A voxel that no quad touches but that is completely walled in by
        // occupied voxels (e.g. the interior of a plain full-cube block like
        // dirt, which only has a one-voxel-thick shell of face quads) should
        // still be treated as "visible" -- otherwise digging into a scaled-up
        // dirt block would reveal nothing rather than more dirt. Flood-fill in
        // from the outside of the grid through unoccupied voxels; anything the
        // flood fill never reaches is enclosed and gets filled in from an
        // adjacent occupied neighbour, the same neighbour-propagation approach
        // BlockPixelSampler already uses for its own interior fill.
        boolean[][][] exteriorReachable = computeExteriorReachable(occupied);
        fillEnclosedInterior(occupied, exteriorReachable, faceDir, pixelX, pixelY, sprite, tintIndex, random);

        // Count occupied voxels
        int occupiedCount = 0;
        for (int x = 0; x < SCALE; x++) {
            for (int y = 0; y < SCALE; y++) {
                for (int z = 0; z < SCALE; z++) {
                    if (occupied[x][y][z]) occupiedCount++;
                }
            }
        }

        if (isAnvil) {
            Microworld.LOGGER.info("VisualBaker: Anvil occupied voxels = {} / {}", occupiedCount, SCALE * SCALE * SCALE);
        }

        // If no voxels were marked, fallback: mark the entire volume with a default sprite (particle icon)
        if (occupiedCount == 0) {
            Microworld.LOGGER.warn("VisualBaker: No occupied voxels for {}, using full volume fallback", state);
            TextureAtlasSprite fallback = model.getParticleIcon();
            if (fallback == null) {
                // Try to get any sprite from the model's quads
                for (BakedQuad q : allQuads) {
                    if (q.getSprite() != null) {
                        fallback = q.getSprite();
                        break;
                    }
                }
            }
            if (fallback == null) {
                // Ultimate fallback: use a missing texture? but we'll just throw.
                Microworld.LOGGER.error("VisualBaker: No sprite available for fallback!");
                return new VisualData(occupied, faceDir, pixelX, pixelY, sprite, tintIndex);
            }
            for (int x = 0; x < SCALE; x++) {
                for (int y = 0; y < SCALE; y++) {
                    for (int z = 0; z < SCALE; z++) {
                        occupied[x][y][z] = true;
                        faceDir[x][y][z] = Direction.UP;
                        pixelX[x][y][z] = 0;
                        pixelY[x][y][z] = 0;
                        sprite[x][y][z] = fallback;
                        tintIndex[x][y][z] = -1;
                    }
                }
            }
            Microworld.LOGGER.warn("VisualBaker: Fallback applied – all voxels marked with sprite {}", fallback.contents().name());
        }

        VisualData data = new VisualData(occupied, faceDir, pixelX, pixelY, sprite, tintIndex);
        long end = System.nanoTime();
        if (isAnvil) {
            Microworld.LOGGER.info("VisualBaker: Anvil baking took {} ms", (end - start) / 1_000_000.0);
        }
        return data;
    }

    private static int floorMin(float v) {
        return (int) Math.floor(v + EPSILON);
    }

    private static int floorMax(float v) {
        return (int) Math.floor(v - EPSILON);
    }

    /**
     * BFS from every unoccupied boundary voxel through unoccupied neighbours.
     * Returns true for every voxel reachable from "outside" the shape without
     * passing through an occupied voxel. Anything unoccupied and NOT in this
     * set is fully enclosed.
     */
    private static boolean[][][] computeExteriorReachable(boolean[][][] occupied) {
        boolean[][][] visited = new boolean[SCALE][SCALE][SCALE];
        ArrayDeque<int[]> queue = new ArrayDeque<>();

        for (int x = 0; x < SCALE; x++) {
            for (int y = 0; y < SCALE; y++) {
                for (int z = 0; z < SCALE; z++) {
                    boolean boundary = x == 0 || x == SCALE - 1
                            || y == 0 || y == SCALE - 1
                            || z == 0 || z == SCALE - 1;
                    if (boundary && !occupied[x][y][z] && !visited[x][y][z]) {
                        visited[x][y][z] = true;
                        queue.add(new int[]{x, y, z});
                    }
                }
            }
        }

        int[] dx = {1, -1, 0, 0, 0, 0};
        int[] dy = {0, 0, 1, -1, 0, 0};
        int[] dz = {0, 0, 0, 0, 1, -1};

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            for (int d = 0; d < 6; d++) {
                int nx = cur[0] + dx[d];
                int ny = cur[1] + dy[d];
                int nz = cur[2] + dz[d];
                if (nx < 0 || nx >= SCALE || ny < 0 || ny >= SCALE || nz < 0 || nz >= SCALE) continue;
                if (visited[nx][ny][nz] || occupied[nx][ny][nz]) continue;
                visited[nx][ny][nz] = true;
                queue.add(new int[]{nx, ny, nz});
            }
        }

        return visited;
    }

    private static void fillEnclosedInterior(
            boolean[][][] occupied,
            boolean[][][] exteriorReachable,
            Direction[][][] faceDir,
            int[][][] pixelX,
            int[][][] pixelY,
            TextureAtlasSprite[][][] sprite,
            int[][][] tintIndex,
            RandomSource random
    ) {
        boolean changed;
        do {
            changed = false;
            for (int x = 0; x < SCALE; x++) {
                for (int y = 0; y < SCALE; y++) {
                    for (int z = 0; z < SCALE; z++) {
                        if (occupied[x][y][z] || exteriorReachable[x][y][z]) continue;

                        List<int[]> candidates = new ArrayList<>();
                        List<Float> weights = new ArrayList<>();

                        for (Direction dir : Direction.values()) {
                            int nx = x + dir.getStepX();
                            int ny = y + dir.getStepY();
                            int nz = z + dir.getStepZ();
                            if (nx < 0 || nx >= SCALE || ny < 0 || ny >= SCALE || nz < 0 || nz >= SCALE) continue;
                            if (!occupied[nx][ny][nz]) continue;
                            float weight = (dir == Direction.UP) ? 0.25f : 1.0f;
                            candidates.add(new int[]{nx, ny, nz});
                            weights.add(weight);
                        }

                        if (!candidates.isEmpty()) {
                            int[] chosen = chooseWeighted(candidates, weights, random);
                            faceDir[x][y][z] = faceDir[chosen[0]][chosen[1]][chosen[2]];
                            pixelX[x][y][z] = pixelX[chosen[0]][chosen[1]][chosen[2]];
                            pixelY[x][y][z] = pixelY[chosen[0]][chosen[1]][chosen[2]];
                            sprite[x][y][z] = sprite[chosen[0]][chosen[1]][chosen[2]];
                            tintIndex[x][y][z] = tintIndex[chosen[0]][chosen[1]][chosen[2]];
                            occupied[x][y][z] = true;
                            changed = true;
                        }
                    }
                }
            }
        } while (changed);
    }

    private static int[] chooseWeighted(List<int[]> candidates, List<Float> weights, RandomSource random) {
        float total = 0.0f;
        for (float w : weights) total += w;
        float value = random.nextFloat() * total;
        for (int i = 0; i < candidates.size(); i++) {
            value -= weights.get(i);
            if (value <= 0.0f) return candidates.get(i);
        }
        return candidates.get(candidates.size() - 1);
    }

    // ---- helper methods (unchanged) ----
    private static float interpolateUV(float[][] pos, float[][] uv, Direction face, float cx, float cy, float cz, int uvIndex) {
        float minA = Float.MAX_VALUE, maxA = -Float.MAX_VALUE;
        float minB = Float.MAX_VALUE, maxB = -Float.MAX_VALUE;
        float[] a = new float[4];
        float[] b = new float[4];
        for (int i = 0; i < 4; i++) {
            float aCoord, bCoord;
            switch (face) {
                case DOWN:
                case UP:
                    aCoord = pos[i][0]; bCoord = pos[i][2]; break;
                case NORTH:
                case SOUTH:
                    aCoord = pos[i][0]; bCoord = pos[i][1]; break;
                case WEST:
                case EAST:
                    aCoord = pos[i][2]; bCoord = pos[i][1]; break;
                default: continue;
            }
            a[i] = aCoord;
            b[i] = bCoord;
            minA = Math.min(minA, aCoord);
            maxA = Math.max(maxA, aCoord);
            minB = Math.min(minB, bCoord);
            maxB = Math.max(maxB, bCoord);
        }
        if (maxA - minA < EPSILON || maxB - minB < EPSILON) return uv[0][uvIndex];

        float pa, pb;
        switch (face) {
            case DOWN:
            case UP:
                pa = cx; pb = cz; break;
            case NORTH:
            case SOUTH:
                pa = cx; pb = cy; break;
            case WEST:
            case EAST:
                pa = cz; pb = cy; break;
            default: return uv[0][uvIndex];
        }

        float s = (pa - minA) / (maxA - minA);
        float t = (pb - minB) / (maxB - minB);
        s = Math.max(0, Math.min(1, s));
        t = Math.max(0, Math.min(1, t));

        float u00 = uv[0][uvIndex], u10 = uv[1][uvIndex], u11 = uv[2][uvIndex], u01 = uv[3][uvIndex];
        float u0 = lerp(u00, u10, s);
        float u1 = lerp(u01, u11, s);
        return lerp(u0, u1, t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static class VisualData {
        public final boolean[][][] occupied;
        public final Direction[][][] faceDir;
        public final int[][][] pixelX;
        public final int[][][] pixelY;
        public final TextureAtlasSprite[][][] sprite;
        public final int[][][] tintIndex;

        public VisualData(boolean[][][] occ, Direction[][][] dir, int[][][] px, int[][][] py,
                          TextureAtlasSprite[][][] spr, int[][][] tint) {
            this.occupied = occ;
            this.faceDir = dir;
            this.pixelX = px;
            this.pixelY = py;
            this.sprite = spr;
            this.tintIndex = tint;
        }
    }
}