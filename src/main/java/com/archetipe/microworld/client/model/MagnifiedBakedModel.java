package com.archetipe.microworld.client.model;

import com.archetipe.microworld.block.MagnifiedBlock;
import com.archetipe.microworld.block.entity.MagnifiedBlockEntity;
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
import java.util.List;

public class MagnifiedBakedModel implements IDynamicBakedModel {

    // This block's visual geometry deliberately fills exactly one 16x16x16
    // chunk section, starting at its own placed position. Geometry that
    // extends beyond its own section's bounds risks being incorrectly culled
    // (or incorrectly kept) depending on which section is actually in view --
    // staying within one section is what makes rendering a single block this
    // oversized safe in the first place. This requires the block to always be
    // placed at a chunk/section-aligned corner (see MicroWorldChunkPopulator).
    private static final float SIZE = 16.0f;

    private final BakedModel baseModel;

    private static final ChunkRenderTypeSet ALL_LAYERS = ChunkRenderTypeSet.of(
            RenderType.solid(),
            RenderType.cutout(),
            RenderType.cutoutMipped(),
            RenderType.translucent()
    );

    public MagnifiedBakedModel(BakedModel base) {
        this.baseModel = base;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData modelData) {
        return ALL_LAYERS;
    }

    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            RandomSource rand,
            ModelData modelData,
            @Nullable RenderType renderType
    ) {
        if (state == null || !(state.getBlock() instanceof MagnifiedBlock)) {
            return baseModel.getQuads(state, side, rand, modelData, renderType);
        }

        BlockPos pos = modelData.get(MagnifiedBlockEntity.POSITION_PROPERTY);
        if (pos == null) {
            return baseModel.getQuads(state, side, rand, modelData, renderType);
        }

        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return baseModel.getQuads(state, side, rand, modelData, renderType);
        }

        if (!(level.getBlockEntity(pos) instanceof MagnifiedBlockEntity be) || be.getOriginalState() == null) {
            return baseModel.getQuads(state, side, rand, modelData, renderType);
        }

        BlockState originalState = be.getOriginalState();
        BakedModel originalModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(originalState);

        // Only draw during the layer the source block's own model actually
        // belongs to -- same reasoning as MicroWorldBakedModel.
        if (renderType != null) {
            ChunkRenderTypeSet originalLayers = originalModel.getRenderTypes(originalState, rand, ModelData.EMPTY);
            if (!originalLayers.contains(renderType)) {
                return new ArrayList<>();
            }
        }

        BlockColors blockColors = Minecraft.getInstance().getBlockColors();
        List<BakedQuad> resultQuads = new ArrayList<>();

        // One giant face per direction, always -- same reasoning as
        // MicroWorldBakedModel's "always synthesize all six directions" fix:
        // a partial-shape source block might not define geometry for every
        // direction (a door's top/bottom), but at this scale we're showing a
        // solid giant cube regardless, so every direction needs a texture.
        for (Direction face : Direction.values()) {
            List<BakedQuad> quads = originalModel.getQuads(originalState, face, rand, ModelData.EMPTY, null);
            if (quads.isEmpty()) {
                quads = originalModel.getQuads(originalState, null, rand, ModelData.EMPTY, null);
            }
            if (quads.isEmpty()) continue;

            BakedQuad source = quads.get(0);
            TextureAtlasSprite sprite = source.getSprite();
            if (sprite == null) continue;

            int tintIndex = source.getTintIndex();
            int vertexColor;
            if (tintIndex >= 0) {
                int rawColor = blockColors.getColor(originalState, level, pos, tintIndex);
                int abgr = (rawColor & 0xFF000000)
                        | ((rawColor & 0x00FF0000) >> 16)
                        | (rawColor & 0x0000FF00)
                        | ((rawColor & 0x000000FF) << 16);
                vertexColor = abgr | 0xFF000000;
            } else {
                vertexColor = 0xFFFFFFFF;
            }

            resultQuads.add(createGiantFace(face, sprite, vertexColor, source.isShade(), source.hasAmbientOcclusion()));
        }

        return resultQuads;
    }

    private BakedQuad createGiantFace(Direction face, TextureAtlasSprite sprite, int color, boolean shade, boolean ao) {
        int[] vertexData = createCubeFace(face, SIZE);

        // Full sprite UV range -- unlike MicroWorldBakedModel, this isn't
        // sampling a single pixel patch, it's stretching the whole texture
        // across the giant face.
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        for (int i = 0; i < 4; i++) {
            int base = i * 8;
            float u, v;
            switch (i) {
                case 0 -> { u = u0; v = v1; }
                case 1 -> { u = u0; v = v0; }
                case 2 -> { u = u1; v = v0; }
                default -> { u = u1; v = v1; }
            }
            vertexData[base + 3] = color;
            vertexData[base + 4] = Float.floatToIntBits(u);
            vertexData[base + 5] = Float.floatToIntBits(v);
        }

        return new BakedQuad(vertexData, -1, face, sprite, shade, ao);
    }

    private static int[] createCubeFace(Direction face, float size) {
        float[][] positions;
        float[] normal;
        switch (face) {
            case DOWN -> {
                positions = new float[][]{{0, 0, 0}, {size, 0, 0}, {size, 0, size}, {0, 0, size}};
                normal = new float[]{0, -1, 0};
            }
            case UP -> {
                positions = new float[][]{{0, size, 0}, {0, size, size}, {size, size, size}, {size, size, 0}};
                normal = new float[]{0, 1, 0};
            }
            case NORTH -> {
                positions = new float[][]{{0, 0, 0}, {0, size, 0}, {size, size, 0}, {size, 0, 0}};
                normal = new float[]{0, 0, -1};
            }
            case SOUTH -> {
                positions = new float[][]{{0, 0, size}, {size, 0, size}, {size, size, size}, {0, size, size}};
                normal = new float[]{0, 0, 1};
            }
            case WEST -> {
                positions = new float[][]{{0, 0, 0}, {0, 0, size}, {0, size, size}, {0, size, 0}};
                normal = new float[]{-1, 0, 0};
            }
            case EAST -> {
                positions = new float[][]{{size, 0, 0}, {size, size, 0}, {size, size, size}, {size, 0, size}};
                normal = new float[]{1, 0, 0};
            }
            default -> throw new IllegalStateException("Unexpected face: " + face);
        }

        int[] data = new int[32];
        int packedNormal = packNormal(normal);
        for (int i = 0; i < 4; i++) {
            int base = i * 8;
            data[base] = Float.floatToIntBits(positions[i][0]);
            data[base + 1] = Float.floatToIntBits(positions[i][1]);
            data[base + 2] = Float.floatToIntBits(positions[i][2]);
            data[base + 3] = 0xFFFFFFFF;
            data[base + 4] = Float.floatToIntBits(0.0f);
            data[base + 5] = Float.floatToIntBits(0.0f);
            data[base + 6] = 0;
            data[base + 7] = packedNormal;
        }
        return data;
    }

    private static int packNormal(float[] n) {
        int x = (int) (n[0] * 127.0f) & 0xFF;
        int y = (int) (n[1] * 127.0f) & 0xFF;
        int z = (int) (n[2] * 127.0f) & 0xFF;
        return x | (y << 8) | (z << 16);
    }

    @Override
    public boolean useAmbientOcclusion() {
        // Same reasoning as MicroWorldBakedModel: flat lighting reads
        // correctly for a manufactured illusion like this; smooth AO does not.
        return false;
    }

    @Override
    public boolean isGui3d() {
        return baseModel.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return baseModel.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return baseModel.getParticleIcon();
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}