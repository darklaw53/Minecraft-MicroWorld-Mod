package com.archetipe.microworld.client.model;

import com.archetipe.microworld.block.MiniatureBlock;
import com.archetipe.microworld.block.entity.MiniatureBlockEntity;
import com.archetipe.microworld.block.entity.MiniatureVoxel;
import com.archetipe.microworld.client.VisualBaker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MiniatureBakedModel implements IDynamicBakedModel {

    private final BakedModel baseModel;

    private static final ChunkRenderTypeSet ALL_LAYERS = ChunkRenderTypeSet.of(
            RenderType.solid(), RenderType.cutout(), RenderType.cutoutMipped(), RenderType.translucent()
    );

    public MiniatureBakedModel(BakedModel base) {
        this.baseModel = base;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData modelData) {
        return ALL_LAYERS;
    }

    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState state, @Nullable Direction side,
            RandomSource rand, ModelData modelData, @Nullable RenderType renderType) {

        if (state == null || !(state.getBlock() instanceof MiniatureBlock)) {
            return baseModel.getQuads(state, side, rand, modelData, renderType);
        }

        BlockPos pos = modelData.get(MiniatureBlockEntity.POSITION_PROPERTY);
        if (pos == null) return Collections.emptyList();

        Level level = Minecraft.getInstance().level;
        if (level == null) return Collections.emptyList();

        if (!(level.getBlockEntity(pos) instanceof MiniatureBlockEntity be)) {
            return Collections.emptyList();
        }

        int scale = be.getScale();
        if (scale <= 0) return Collections.emptyList();

        Map<Integer, MiniatureVoxel> voxels = be.getVoxels();
        if (voxels.isEmpty()) return Collections.emptyList();

        List<Map.Entry<Integer, MiniatureVoxel>> sampled = new ArrayList<>();
        List<Map.Entry<Integer, MiniatureVoxel>> real = new ArrayList<>();
        for (Map.Entry<Integer, MiniatureVoxel> e : voxels.entrySet()) {
            if (e.getValue().sampled()) sampled.add(e);
            else real.add(e);
        }

        List<BakedQuad> out = new ArrayList<>();
        float vs = 1.0f / scale;

        if (!sampled.isEmpty()) {
            renderSampledVoxels(sampled, scale, vs, side, rand, renderType, level, pos, out);
        }
        if (!real.isEmpty()) {
            renderRealVoxels(real, scale, vs, side, rand, renderType, level, pos, out);
        }
        return out;
    }

    private void renderSampledVoxels(
            List<Map.Entry<Integer, MiniatureVoxel>> entries,
            int scale, float vs, @Nullable Direction side, RandomSource rand,
            @Nullable RenderType renderType, Level level, BlockPos pos, List<BakedQuad> out) {

        BlockState source = entries.get(0).getValue().state();
        BakedModel sourceModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(source);

        if (renderType != null) {
            ChunkRenderTypeSet sourceLayers = sourceModel.getRenderTypes(source, rand, ModelData.EMPTY);
            if (!sourceLayers.contains(renderType)) return;
        }

        VisualBaker.VisualData visual = null;
        boolean applyVisualCull = scale == VisualBaker.SCALE;
        if (applyVisualCull) {
            visual = VisualBaker.getVisualData(source);
        }

        @SuppressWarnings("unchecked")
        List<BakedQuad>[] quadsByDir = new List[6];
        List<BakedQuad> nullSideQuads = sourceModel.getQuads(source, null, rand, ModelData.EMPTY, null);
        for (Direction d : Direction.values()) {
            List<BakedQuad> q = sourceModel.getQuads(source, d, rand, ModelData.EMPTY, null);
            if (q.isEmpty()) q = nullSideQuads;
            quadsByDir[d.ordinal()] = q;
        }
        List<BakedQuad> fallbackQuads = nullSideQuads;
        if (fallbackQuads.isEmpty()) {
            for (List<BakedQuad> list : quadsByDir) {
                if (!list.isEmpty()) { fallbackQuads = list; break; }
            }
        }
        if (fallbackQuads.isEmpty()) return;
        for (int i = 0; i < 6; i++) {
            if (quadsByDir[i].isEmpty()) quadsByDir[i] = fallbackQuads;
        }

        BlockColors blockColors = Minecraft.getInstance().getBlockColors();
        Direction[] faces = (side == null) ? Direction.values() : new Direction[]{side};

        // IMPORTANT: build sampledIdx AFTER the cull. Voxels that fail the
        // cull are invisible; if they stay in this set they will occlude
        // their neighbors' faces (hide them), and the mini ends up showing
        // only its outermost shell.
        Set<Integer> sampledIdx = new HashSet<>(entries.size());
        for (Map.Entry<Integer, MiniatureVoxel> e : entries) {
            int idx = e.getKey();
            if (applyVisualCull) {
                int x = idx % scale;
                int rem = idx / scale;
                int z = rem % scale;
                int y = rem / scale;
                if (x < VisualBaker.SCALE && y < VisualBaker.SCALE && z < VisualBaker.SCALE
                        && !visual.occupied[x][y][z]) {
                    continue;
                }
            }
            sampledIdx.add(idx);
        }

        for (Map.Entry<Integer, MiniatureVoxel> entry : entries) {
            int idx = entry.getKey();
            short packed = entry.getValue().pixel();

            int x = idx % scale;
            int rem = idx / scale;
            int z = rem % scale;
            int y = rem / scale;

            if (applyVisualCull
                    && x < VisualBaker.SCALE && y < VisualBaker.SCALE && z < VisualBaker.SCALE
                    && !visual.occupied[x][y][z]) {
                continue;
            }

            int dirOrd = (packed >> 8) & 0x7;
            Direction refDir = Direction.values()[dirOrd];
            int px = (packed >> 4) & 0xF;
            int py = packed & 0xF;

            List<BakedQuad> sourceQuads = quadsByDir[refDir.ordinal()];

            for (Direction face : faces) {
                int nx = x + face.getStepX();
                int ny = y + face.getStepY();
                int nz = z + face.getStepZ();
                boolean neighborFilled;
                if (nx < 0 || nx >= scale || ny < 0 || ny >= scale || nz < 0 || nz >= scale) {
                    neighborFilled = false;
                } else {
                    int nIdx = (ny * scale + nz) * scale + nx;
                    neighborFilled = sampledIdx.contains(nIdx);
                }
                if (neighborFilled) continue;

                for (BakedQuad srcQuad : sourceQuads) {
                    TextureAtlasSprite sprite = srcQuad.getSprite();
                    if (sprite == null) continue;

                    int tintIndex = srcQuad.getTintIndex();
                    int tintColor = 0xFFFFFFFF;
                    if (tintIndex >= 0) {
                        int raw = blockColors.getColor(source, level, pos, tintIndex);
                        tintColor = argbToAbgr(raw) | 0xFF000000;
                    }

                    out.add(createSampledVoxelFace(
                            face, x, y, z, vs, sprite, px, py, tintColor, srcQuad.isShade()));
                }
            }
        }
    }

    private void renderRealVoxels(
            List<Map.Entry<Integer, MiniatureVoxel>> entries,
            int scale, float vs, @Nullable Direction side, RandomSource rand,
            @Nullable RenderType renderType, Level level, BlockPos pos, List<BakedQuad> out) {

        Minecraft mc = Minecraft.getInstance();
        BlockColors blockColors = mc.getBlockColors();

        for (Map.Entry<Integer, MiniatureVoxel> entry : entries) {
            int idx = entry.getKey();
            BlockState blockState = entry.getValue().state();
            if (blockState.isAir()) continue;

            int x = idx % scale;
            int rem = idx / scale;
            int z = rem % scale;
            int y = rem / scale;

            BakedModel blockModel = mc.getBlockRenderer().getBlockModel(blockState);
            if (blockModel == null) continue;

            if (renderType != null) {
                ChunkRenderTypeSet blockLayers = blockModel.getRenderTypes(blockState, rand, ModelData.EMPTY);
                if (!blockLayers.contains(renderType)) continue;
            }

            List<BakedQuad> blockQuads = new ArrayList<>();
            if (side != null) {
                blockQuads.addAll(blockModel.getQuads(blockState, side, rand, ModelData.EMPTY, null));
            } else {
                for (Direction d : Direction.values()) {
                    blockQuads.addAll(blockModel.getQuads(blockState, d, rand, ModelData.EMPTY, null));
                }
                blockQuads.addAll(blockModel.getQuads(blockState, null, rand, ModelData.EMPTY, null));
            }
            if (blockQuads.isEmpty()) continue;

            for (BakedQuad q : blockQuads) {
                int tintIndex = q.getTintIndex();
                int tintColor = 0xFFFFFFFF;
                if (tintIndex >= 0) {
                    int raw = blockColors.getColor(blockState, level, pos, tintIndex);
                    tintColor = argbToAbgr(raw) | 0xFF000000;
                }
                BakedQuad scaled = scaleQuad(q, x, y, z, vs, tintColor);
                if (scaled != null) out.add(scaled);
            }
        }
    }

    private static BakedQuad scaleQuad(BakedQuad q, int vx, int vy, int vz, float vs, int tintColor) {
        int[] src = q.getVertices();
        if (src.length != 32) return null;

        int[] dst = new int[32];
        float ox = vx * vs, oy = vy * vs, oz = vz * vs;

        for (int i = 0; i < 4; i++) {
            int b = i * 8;
            float x = Float.intBitsToFloat(src[b]);
            float y = Float.intBitsToFloat(src[b + 1]);
            float z = Float.intBitsToFloat(src[b + 2]);

            dst[b]     = Float.floatToIntBits(x * vs + ox);
            dst[b + 1] = Float.floatToIntBits(y * vs + oy);
            dst[b + 2] = Float.floatToIntBits(z * vs + oz);
            dst[b + 3] = tintColor;
            dst[b + 4] = src[b + 4];
            dst[b + 5] = src[b + 5];
            dst[b + 6] = src[b + 6];
            dst[b + 7] = src[b + 7];
        }
        return new BakedQuad(dst, -1, q.getDirection(), q.getSprite(), q.isShade(), q.hasAmbientOcclusion());
    }

    private static int argbToAbgr(int argb) {
        return (argb & 0xFF000000)
                | ((argb & 0x00FF0000) >> 16)
                | ((argb & 0x0000FF00))
                | ((argb & 0x000000FF) << 16);
    }

    private static BakedQuad createSampledVoxelFace(
            Direction face, int vx, int vy, int vz, float vs,
            TextureAtlasSprite sprite, int px, int py, int tintColor, boolean shade) {

        float x0 = vx * vs, x1 = x0 + vs;
        float y0 = vy * vs, y1 = y0 + vs;
        float z0 = vz * vs, z1 = z0 + vs;

        float[][] positions;
        float[] normal;
        switch (face) {
            case DOWN -> {
                positions = new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}};
                normal = new float[]{0, -1, 0};
            }
            case UP -> {
                positions = new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
                normal = new float[]{0, 1, 0};
            }
            case NORTH -> {
                positions = new float[][]{{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}};
                normal = new float[]{0, 0, -1};
            }
            case SOUTH -> {
                positions = new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
                normal = new float[]{0, 0, 1};
            }
            case WEST -> {
                positions = new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}};
                normal = new float[]{-1, 0, 0};
            }
            case EAST -> {
                positions = new float[][]{{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}};
                normal = new float[]{1, 0, 0};
            }
            default -> throw new IllegalStateException();
        }

        float su0 = sprite.getU0(), su1 = sprite.getU1();
        float sv0 = sprite.getV0(), sv1 = sprite.getV1();
        float u0 = su0 + (su1 - su0) * (px / 16f);
        float u1 = su0 + (su1 - su0) * ((px + 1) / 16f);
        float v0 = sv0 + (sv1 - sv0) * (py / 16f);
        float v1 = sv0 + (sv1 - sv0) * ((py + 1) / 16f);

        int[] data = new int[32];
        int packedNormal = packNormal(normal);
        for (int i = 0; i < 4; i++) {
            int b = i * 8;
            data[b]     = Float.floatToIntBits(positions[i][0]);
            data[b + 1] = Float.floatToIntBits(positions[i][1]);
            data[b + 2] = Float.floatToIntBits(positions[i][2]);
            data[b + 3] = tintColor;
            float u, v;
            switch (i) {
                case 0 -> { u = u0; v = v1; }
                case 1 -> { u = u0; v = v0; }
                case 2 -> { u = u1; v = v0; }
                default -> { u = u1; v = v1; }
            }
            data[b + 4] = Float.floatToIntBits(u);
            data[b + 5] = Float.floatToIntBits(v);
            data[b + 6] = 0;
            data[b + 7] = packedNormal;
        }
        return new BakedQuad(data, -1, face, sprite, shade, false);
    }

    private static int packNormal(float[] n) {
        int x = (int) (n[0] * 127f) & 0xFF;
        int y = (int) (n[1] * 127f) & 0xFF;
        int z = (int) (n[2] * 127f) & 0xFF;
        return x | (y << 8) | (z << 16);
    }

    @Override public boolean useAmbientOcclusion() { return false; }
    @Override public boolean isGui3d() { return baseModel.isGui3d(); }
    @Override public boolean usesBlockLight() { return baseModel.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleIcon() { return baseModel.getParticleIcon(); }
    @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
}