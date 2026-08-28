package com.archetipe.microworld.client.world;

import com.archetipe.microworld.world.CollisionVoxelizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Client‑only class that samples a block's model and generates a packed
 * pixel‑data array. This must be called on the render thread.
 */
public final class BlockPixelSampler {

    private static final float EPSILON = 1.0e-5f;
    private static final Map<BlockState, List<BakedQuad>> quadCache = new HashMap<>();

    private BlockPixelSampler() {}

    /**
     * Samples the given block state at the given scale and returns a packed short array.
     * Each entry encodes (direction, pixelX, pixelY) for one voxel in the enlarged structure.
     *
     * @param sourceState  the block to sample
     * @param scale        enlargement factor (cubic)
     * @param seed         seed for random choices (e.g., origin.asLong())
     * @param origin       the position where the master block will be placed (used for level access)
     * @param level        the client level (needed for collision shape)
     * @return a packed short array of length scale³
     */
    public static short[] sampleBlock(BlockState sourceState, int scale, long seed, BlockPos origin, Level level) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getBlockRenderer().getBlockModel(sourceState);
        RandomSource random = RandomSource.create(seed);
        float modelScale = detectModelScale(model, random);

        // ---- Collision voxelisation (uses level and block shape) ----
        boolean[][][] collision = CollisionVoxelizer.create(level, origin, sourceState, scale);

        // ---- Generate exterior and interior pixel data ----
        PixelData[][][] generated = new PixelData[scale][scale][scale];

        generateExterior(sourceState, model, scale, random, generated, collision, modelScale);
        generateInterior(sourceState, scale, random, generated, collision);

        // ---- Pack into short[] ----
        int total = scale * scale * scale;
        short[] pixelData = new short[total];
        for (int x = 0; x < scale; x++) {
            for (int y = 0; y < scale; y++) {
                for (int z = 0; z < scale; z++) {
                    int idx = (y * scale + z) * scale + x;
                    PixelData data = generated[x][y][z];
                    if (data != null) {
                        pixelData[idx] = packPixelData(data.referenceDirection, data.pixelX, data.pixelY);
                    } else {
                        pixelData[idx] = 0;
                    }
                }
            }
        }
        return pixelData;
    }

    // ======================== Original sampling methods (copied verbatim) ========================

    private static short packPixelData(Direction dir, int px, int py) {
        return (short) ((dir.ordinal() << 8) | (px << 4) | py);
    }

    private static boolean isBottomCorner(int x, int z, int scale) {
        return (x == 0 || x == scale - 1) && (z == 0 || z == scale - 1);
    }

    private static void generateExterior(BlockState sourceState, BakedModel model, int scale,
                                         RandomSource random, PixelData[][][] generated,
                                         boolean[][][] collision, float modelScale) {
        for (int x = 0; x < scale; x++) {
            for (int y = 0; y < scale; y++) {
                for (int z = 0; z < scale; z++) {
                    if (!collision[x][y][z]) continue;

                    List<Direction> faces = getExposedFaces(x, y, z, scale, collision);
                    if (faces.isEmpty()) continue;

                    if (y == 0 && isBottomCorner(x, z, scale)) {
                        faces.remove(Direction.DOWN);
                    }
                    if (faces.isEmpty()) continue;

                    Direction selectedFace = chooseReferenceDirection(faces, random);
                    PixelData data = sampleFaceUniversal(sourceState, model, selectedFace, x, y, z, scale, random, modelScale);
                    if (data != null) {
                        generated[x][y][z] = data;
                    }
                }
            }
        }
    }

    private static Direction chooseReferenceDirection(List<Direction> exposedFaces, RandomSource random) {
        // Build a list that excludes the top face
        List<Direction> candidates = new ArrayList<>(exposedFaces);
        candidates.remove(Direction.UP);

        // If there is any other exposed face, pick randomly from those
        if (!candidates.isEmpty()) {
            return candidates.get(random.nextInt(candidates.size()));
        } else {
            // Only the top face is exposed – fall back to it
            return Direction.UP;
        }
    }

    private static float detectModelScale(BakedModel model, RandomSource random) {
        // Check all quads for any vertex coordinate > 1.1
        for (Direction dir : Direction.values()) {
            for (BakedQuad quad : model.getQuads(null, dir, random)) {
                int[] verts = quad.getVertices();
                for (int i = 0; i < 4; i++) {
                    float x = Float.intBitsToFloat(verts[i * 8]);
                    float y = Float.intBitsToFloat(verts[i * 8 + 1]);
                    float z = Float.intBitsToFloat(verts[i * 8 + 2]);
                    if (x > 1.1f || y > 1.1f || z > 1.1f) {
                        return 16.0f;   // model uses 0–16 coordinates
                    }
                }
            }
        }
        return 1.0f;   // model uses 0–1 coordinates
    }

    private static void generateInterior(BlockState sourceState, int scale, RandomSource random,
                                         PixelData[][][] generated, boolean[][][] collision) {
        int maxShell = (scale - 1) / 2;
        for (int shell = 1; shell <= maxShell; shell++) {
            for (int x = shell; x < scale - shell; x++) {
                for (int y = shell; y < scale - shell; y++) {
                    for (int z = shell; z < scale - shell; z++) {
                        if (!collision[x][y][z] || generated[x][y][z] != null) continue;

                        List<PixelData> candidates = new ArrayList<>();
                        List<Float> weights = new ArrayList<>();

                        for (Direction dir : Direction.values()) {
                            int nx = x + dir.getStepX();
                            int ny = y + dir.getStepY();
                            int nz = z + dir.getStepZ();
                            if (nx < 0 || nx >= scale || ny < 0 || ny >= scale || nz < 0 || nz >= scale) continue;
                            if (!collision[nx][ny][nz]) continue;
                            PixelData neighbor = generated[nx][ny][nz];
                            if (neighbor == null) continue;
                            float weight = (dir == Direction.UP) ? 0.25f : 1.0f;
                            candidates.add(neighbor);
                            weights.add(weight);
                        }
                        if (!candidates.isEmpty()) {
                            generated[x][y][z] = chooseWeighted(candidates, weights, random);
                        }
                    }
                }
            }
        }
        fillRemaining(scale, random, generated, collision);
    }

    private static void fillRemaining(int scale, RandomSource random,
                                      PixelData[][][] generated, boolean[][][] collision) {
        boolean changed;
        do {
            changed = false;
            for (int x = 0; x < scale; x++) {
                for (int y = 0; y < scale; y++) {
                    for (int z = 0; z < scale; z++) {
                        if (!collision[x][y][z] || generated[x][y][z] != null) continue;

                        List<PixelData> candidates = new ArrayList<>();
                        List<Float> weights = new ArrayList<>();

                        for (Direction dir : Direction.values()) {
                            int nx = x + dir.getStepX();
                            int ny = y + dir.getStepY();
                            int nz = z + dir.getStepZ();
                            if (nx < 0 || nx >= scale || ny < 0 || ny >= scale || nz < 0 || nz >= scale) continue;
                            if (!collision[nx][ny][nz]) continue;
                            PixelData neighbor = generated[nx][ny][nz];
                            if (neighbor == null) continue;
                            float weight = (dir == Direction.UP) ? 0.25f : 1.0f;
                            candidates.add(neighbor);
                            weights.add(weight);
                        }
                        if (!candidates.isEmpty()) {
                            generated[x][y][z] = chooseWeighted(candidates, weights, random);
                            changed = true;
                        }
                    }
                }
            }
        } while (changed);
    }

    private static PixelData chooseWeighted(List<PixelData> candidates, List<Float> weights, RandomSource random) {
        float total = 0.0f;
        for (float w : weights) total += w;
        float value = random.nextFloat() * total;
        for (int i = 0; i < candidates.size(); i++) {
            value -= weights.get(i);
            if (value <= 0.0f) return candidates.get(i);
        }
        return candidates.get(candidates.size() - 1);
    }

    private static PixelData sampleFaceUniversal(BlockState sourceState, BakedModel model,
                                                 Direction exposedFace, int x, int y, int z,
                                                 int scale, RandomSource random, float modelScale) {
        List<BakedQuad> allQuads = getAllQuadsForState(sourceState, model, random);
        if (allQuads.isEmpty()) return null;

        float px = (x + 0.5f) / scale * modelScale;
        float py = (y + 0.5f) / scale * modelScale;
        float pz = (z + 0.5f) / scale * modelScale;

        switch (exposedFace) {
            case DOWN  -> py = 0.0f;                     // bottom of the block
            case UP    -> py = modelScale;               // top
            case NORTH -> pz = 0.0f;
            case SOUTH -> pz = modelScale;
            case WEST  -> px = 0.0f;
            case EAST  -> px = modelScale;
        }

        List<BakedQuad> matchingQuads = new ArrayList<>();
        for (BakedQuad quad : allQuads) {
            if (quad.getDirection() == exposedFace) matchingQuads.add(quad);
        }
        List<BakedQuad> candidateQuads = matchingQuads.isEmpty() ? allQuads : matchingQuads;

        BakedQuad bestQuad = null;
        float[] bestUV = null;
        float bestDist = Float.MAX_VALUE;

        for (BakedQuad quad : candidateQuads) {
            float[] uv = getUVFromQuad(quad, exposedFace, px, py, pz);
            if (uv == null) continue;

            int[] vertices = quad.getVertices();
            float[][] uvCorners = new float[4][2];
            for (int i = 0; i < 4; i++) {
                int idx = i * 8;
                uvCorners[i][0] = Float.intBitsToFloat(vertices[idx + 4]);
                uvCorners[i][1] = Float.intBitsToFloat(vertices[idx + 5]);
            }
            float avgU = (uvCorners[0][0] + uvCorners[1][0] + uvCorners[2][0] + uvCorners[3][0]) / 4.0f;
            float avgV = (uvCorners[0][1] + uvCorners[1][1] + uvCorners[2][1] + uvCorners[3][1]) / 4.0f;
            float dist = Math.abs(uv[0] - avgU) + Math.abs(uv[1] - avgV);
            if (dist < bestDist) {
                bestDist = dist;
                bestQuad = quad;
                bestUV = uv;
            }
        }
        if (bestQuad == null || bestUV == null) return null;

        TextureAtlasSprite sprite = bestQuad.getSprite();
        int pixelX = getTexturePixelX(sprite, bestUV[0]);
        int pixelY = getTexturePixelY(sprite, bestUV[1]);
        return new PixelData(exposedFace, pixelX, pixelY);
    }

    private static List<BakedQuad> getAllQuadsForState(BlockState state, BakedModel model, RandomSource random) {
        return quadCache.computeIfAbsent(state, s -> {
            List<BakedQuad> quads = new ArrayList<>();
            for (Direction dir : Direction.values()) {
                quads.addAll(model.getQuads(s, dir, random));
            }
            quads.addAll(model.getQuads(s, null, random));
            return quads;
        });
    }

    private static float[] getUVFromQuad(BakedQuad quad, Direction direction,
                                         float px, float py, float pz) {
        int[] vertices = quad.getVertices();
        float[][] pos = new float[4][3];
        float[][] uv = new float[4][2];
        for (int i = 0; i < 4; i++) {
            int idx = i * 8;
            pos[i][0] = Float.intBitsToFloat(vertices[idx]);
            pos[i][1] = Float.intBitsToFloat(vertices[idx + 1]);
            pos[i][2] = Float.intBitsToFloat(vertices[idx + 2]);
            uv[i][0] = Float.intBitsToFloat(vertices[idx + 4]);
            uv[i][1] = Float.intBitsToFloat(vertices[idx + 5]);
        }

        float faceX, faceY;
        if (direction == Direction.UP || direction == Direction.DOWN) {
            faceX = px; faceY = pz;
        } else if (direction == Direction.NORTH || direction == Direction.SOUTH) {
            faceX = px; faceY = py;
        } else {
            faceX = pz; faceY = py;
        }
        if (direction == Direction.UP) faceY = 1.0f - faceY;

        float[] fx = new float[4], fy = new float[4];
        for (int i = 0; i < 4; i++) {
            fx[i] = getFaceX(direction, pos[i]);
            fy[i] = getFaceY(direction, pos[i]);
        }

        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            minX = Math.min(minX, fx[i]); maxX = Math.max(maxX, fx[i]);
            minY = Math.min(minY, fy[i]); maxY = Math.max(maxY, fy[i]);
        }
        if (faceX < minX - EPSILON || faceX > maxX + EPSILON ||
                faceY < minY - EPSILON || faceY > maxY + EPSILON) {
            return null;
        }
        float width = maxX - minX, height = maxY - minY;
        if (width <= EPSILON || height <= EPSILON) return null;

        float localX = normalize(faceX, minX, maxX);
        float localY = normalize(faceY, minY, maxY);

        int leftBottom = -1, rightBottom = -1, leftTop = -1, rightTop = -1;
        float bestLB = Float.MAX_VALUE, bestRB = Float.MAX_VALUE;
        float bestLT = Float.MAX_VALUE, bestRT = Float.MAX_VALUE;

        for (int i = 0; i < 4; i++) {
            float dxL = fx[i] - minX, dxR = fx[i] - maxX;
            float dyB = fy[i] - minY, dyT = fy[i] - maxY;
            float dLB = dxL*dxL + dyB*dyB;
            float dRB = dxR*dxR + dyB*dyB;
            float dLT = dxL*dxL + dyT*dyT;
            float dRT = dxR*dxR + dyT*dyT;
            if (dLB < bestLB) { bestLB = dLB; leftBottom = i; }
            if (dRB < bestRB) { bestRB = dRB; rightBottom = i; }
            if (dLT < bestLT) { bestLT = dLT; leftTop = i; }
            if (dRT < bestRT) { bestRT = dRT; rightTop = i; }
        }
        if (leftBottom < 0 || rightBottom < 0 || leftTop < 0 || rightTop < 0) return null;

        float leftU = lerp(uv[leftBottom][0], uv[leftTop][0], localY);
        float rightU = lerp(uv[rightBottom][0], uv[rightTop][0], localY);
        float leftV = lerp(uv[leftBottom][1], uv[leftTop][1], localY);
        float rightV = lerp(uv[rightBottom][1], uv[rightTop][1], localY);
        float finalU = lerp(leftU, rightU, localX);
        float finalV = lerp(leftV, rightV, localX);
        return new float[]{finalU, finalV};
    }

    private static float getFaceX(Direction direction, float[] pos) {
        return switch (direction) {
            case DOWN, NORTH, SOUTH -> pos[0];
            case EAST, WEST -> pos[2];
            case UP -> pos[0];
        };
    }

    private static float getFaceY(Direction direction, float[] pos) {
        return switch (direction) {
            case DOWN -> pos[2];
            case NORTH, SOUTH, EAST, WEST -> pos[1];
            case UP -> pos[2];
        };
    }

    private static List<Direction> getExposedFaces(int x, int y, int z, int scale, boolean[][][] collision) {
        List<Direction> faces = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            int nx = x + dir.getStepX();
            int ny = y + dir.getStepY();
            int nz = z + dir.getStepZ();
            if (nx < 0 || nx >= scale || ny < 0 || ny >= scale || nz < 0 || nz >= scale) {
                faces.add(dir);
            } else if (!collision[nx][ny][nz]) {
                faces.add(dir);
            }
        }
        return faces;
    }

    private static int getTexturePixelX(TextureAtlasSprite sprite, float u) {
        float w = sprite.getU1() - sprite.getU0();
        if (w <= 0) return 0;
        int pixel = (int) (((u - sprite.getU0()) / w) * 16.0f);
        return clampPixel(pixel);
    }

    private static int getTexturePixelY(TextureAtlasSprite sprite, float v) {
        float h = sprite.getV1() - sprite.getV0();
        if (h <= 0) return 0;
        int pixel = (int) (((v - sprite.getV0()) / h) * 16.0f);
        return clampPixel(pixel);
    }

    private static int clampPixel(int pixel) {
        return Math.max(0, Math.min(15, pixel));
    }

    private static float normalize(float value, float min, float max) {
        if (Math.abs(max - min) <= EPSILON) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, (value - min) / (max - min)));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // ---- Helper data class ----
    private static final class PixelData {
        private final Direction referenceDirection;
        private final int pixelX;
        private final int pixelY;
        private PixelData(Direction referenceDirection, int pixelX, int pixelY) {
            this.referenceDirection = referenceDirection;
            this.pixelX = pixelX;
            this.pixelY = pixelY;
        }
    }
}