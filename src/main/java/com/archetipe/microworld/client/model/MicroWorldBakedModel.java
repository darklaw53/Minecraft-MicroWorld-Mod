package com.archetipe.microworld.client.model;

import com.archetipe.microworld.block.MicroWorldBlock;
import com.archetipe.microworld.block.entity.MicroWorldBlockEntity;
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
import java.util.List;

public class MicroWorldBakedModel implements IDynamicBakedModel {

    private final BakedModel baseModel;

    // Every render layer a source block could plausibly need. MicroWorldBlock's own
    // base model is only registered for one layer (solid), but the blocks it can
    // represent (fences, leaves, glass, etc.) span solid/cutout/cutout_mipped/
    // translucent. Without this override, chunk rendering only ever calls
    // getQuads() during the solid pass, so anything whose *original* model lives in
    // a different layer (e.g. fences, which use cutout) never gets queried at all.
    private static final ChunkRenderTypeSet ALL_LAYERS = ChunkRenderTypeSet.of(
            RenderType.solid(),
            RenderType.cutout(),
            RenderType.cutoutMipped(),
            RenderType.translucent()
    );

    public MicroWorldBakedModel(BakedModel base) {
        this.baseModel = base;
    }

    private static int argbToAbgr(int argb) {
        return (argb & 0xFF000000) |
                ((argb & 0x00FF0000) >> 16) |
                (argb & 0x0000FF00) |
                ((argb & 0x000000FF) << 16);
    }

    private static int forceOpaque(int color) {
        return color | 0xFF000000;
    }

    // ---- Render type routing ----

    @Override
    public ChunkRenderTypeSet getRenderTypes(
            BlockState state,
            RandomSource rand,
            ModelData modelData
    ) {
        return ALL_LAYERS;
    }

    // ---- Main model logic ----

    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            RandomSource rand,
            ModelData modelData,
            @Nullable RenderType renderType
    ) {
        if (state == null || !(state.getBlock() instanceof MicroWorldBlock)) {
            return baseModel.getQuads(state, side, rand, modelData, renderType);
        }

        BlockPos pos = modelData.get(MicroWorldBlockEntity.POSITION_PROPERTY);

        if (pos == null) {
            TextureAtlasSprite fallbackSprite = baseModel.getParticleIcon();
            if (fallbackSprite == null) {
                return baseModel.getQuads(state, side, rand, modelData, renderType);
            }
            return generateFallbackQuads(side, fallbackSprite);
        }

        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return baseModel.getQuads(state, side, rand, modelData, renderType);
        }

        if (!(level.getBlockEntity(pos) instanceof MicroWorldBlockEntity be)) {
            return baseModel.getQuads(state, side, rand, modelData, renderType);
        }

        MicroWorldBlockEntity masterBE = be.isMaster() ? be : be.getMasterBE();
        if (masterBE == null || masterBE.getOriginalState() == null) {
            return baseModel.getQuads(state, side, rand, modelData, renderType);
        }

        BlockState originalState = masterBE.getOriginalState();
        BlockPos originalPos = masterBE.getOriginalPos();
        if (originalPos == null) originalPos = masterBE.getBlockPos();

        int scale = masterBE.getScale();

        // ---- Visual-shape mask ----
        // Structure placement is driven by the source block's COLLISION shape
        // (CollisionVoxelizer), which can be larger than what the block actually
        // looks like -- e.g. a resource pack shrinking a fence's model below its
        // vanilla collision box, or any block whose hitbox is bigger than its
        // render geometry. VisualBaker separately bakes, per voxel, whether the
        // block's real rendered geometry reaches that voxel. If this voxel was
        // only placed because of collision but the model never actually draws
        // there, render nothing -- the block stays solid/collidable, it's just
        // invisible, so only the voxels that match the true visual shape show up.
        BlockPos structureOrigin = masterBE.getStructureOrigin();
        if (structureOrigin != null && scale == VisualBaker.SCALE) {
            int localX = pos.getX() - structureOrigin.getX();
            int localY = pos.getY() - structureOrigin.getY();
            int localZ = pos.getZ() - structureOrigin.getZ();

            if (localX >= 0 && localX < VisualBaker.SCALE &&
                    localY >= 0 && localY < VisualBaker.SCALE &&
                    localZ >= 0 && localZ < VisualBaker.SCALE) {
                VisualBaker.VisualData visual = VisualBaker.getVisualData(originalState);
                if (!visual.occupied[localX][localY][localZ]) {
                    return new ArrayList<>();
                }
            }
        }

        BakedModel originalModel = Minecraft.getInstance()
                .getBlockRenderer()
                .getBlockModel(originalState);

        // We get called once per layer (see getRenderTypes above). Only produce
        // quads during the layer that the *original* block's own model actually
        // belongs to; every other layer should see nothing from us, or the block
        // would be drawn multiple times / z-fight with itself.
        if (renderType != null) {
            ChunkRenderTypeSet originalLayers = originalModel.getRenderTypes(originalState, rand, ModelData.EMPTY);
            if (!originalLayers.contains(renderType)) {
                return new ArrayList<>();
            }
        }

        // ---- Get ALL quads from the model (both directional and null) ----
        // Deliberately pass null as the renderType here, not the incoming
        // renderType -- we already confirmed above (via getRenderTypes) this is
        // the correct layer; querying with a mismatched/narrow renderType is what
        // caused non-solid sources to come back with zero quads.
        List<BakedQuad> allQuads = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            allQuads.addAll(originalModel.getQuads(originalState, dir, rand, ModelData.EMPTY, null));
        }
        allQuads.addAll(originalModel.getQuads(originalState, null, rand, ModelData.EMPTY, null));

        if (allQuads.isEmpty()) {
            return new ArrayList<>();
        }

        // ---- Choose reference direction ----
        int dirValue = state.getValue(MicroWorldBlock.DIR);
        Direction referenceDirection = Direction.values()[dirValue];

        List<BakedQuad> referenceQuads = originalModel.getQuads(
                originalState, referenceDirection, rand, ModelData.EMPTY, null
        );
        if (referenceQuads.isEmpty() && referenceDirection != Direction.UP) {
            referenceDirection = Direction.UP;
            referenceQuads = originalModel.getQuads(
                    originalState, Direction.UP, rand, ModelData.EMPTY, null
            );
        }
        if (referenceQuads.isEmpty()) {
            referenceQuads = originalModel.getQuads(
                    originalState, null, rand, ModelData.EMPTY, null
            );
        }

        boolean useGeometrySprite = referenceQuads.isEmpty();

        // Only used in the rare useGeometrySprite fallback (referenceQuads
        // completely empty for this whole model): grab any sprite at all from
        // the source model's quads, since we no longer have a single
        // "current" geometry quad to fall back on per direction (see below).
        TextureAtlasSprite fallbackSprite = null;
        int fallbackTint = -1;
        boolean fallbackShade = true;
        boolean fallbackAo = true;
        if (useGeometrySprite) {
            for (BakedQuad q : allQuads) {
                if (q.getSprite() != null) {
                    fallbackSprite = q.getSprite();
                    fallbackTint = q.getTintIndex();
                    fallbackShade = q.isShade();
                    fallbackAo = q.hasAmbientOcclusion();
                    break;
                }
            }
        }

        BlockColors blockColors = Minecraft.getInstance().getBlockColors();

        List<BakedQuad> resultQuads = new ArrayList<>();

        // Generate one full 1x1x1 cube face per canonical direction, using
        // reference-quad sprites (or the fallback above). We deliberately do
        // NOT restrict this to only the directions the original model happens
        // to define geometry for: some partial-shape source blocks (a door,
        // for instance) never define a quad for one or more directions at
        // all, because in the real game that face is always hidden against
        // another block (a door's top/bottom seam against its other half).
        // Once reconstructed as a detached MicroWorld structure, that face
        // can become genuinely exposed -- e.g. the cut top of a lower door
        // half -- so every direction always gets a face rather than silently
        // staying an open hole for every voxel in the structure.
        for (Direction face : Direction.values()) {
            List<TextureAtlasSprite> sprites = new ArrayList<>();
            List<Integer> tintIndices = new ArrayList<>();
            List<Boolean> shades = new ArrayList<>();
            List<Boolean> aos = new ArrayList<>();

            if (useGeometrySprite) {
                if (fallbackSprite != null) {
                    sprites.add(fallbackSprite);
                    tintIndices.add(fallbackTint);
                    shades.add(fallbackShade);
                    aos.add(fallbackAo);
                }
            } else {
                // Use reference quads' sprites
                for (BakedQuad refQuad : referenceQuads) {
                    TextureAtlasSprite sprite = refQuad.getSprite();
                    if (sprite != null) {
                        sprites.add(sprite);
                        tintIndices.add(refQuad.getTintIndex());
                        shades.add(refQuad.isShade());
                        aos.add(refQuad.hasAmbientOcclusion());
                    }
                }
            }

            if (sprites.isEmpty()) continue;

            for (int i = 0; i < sprites.size(); i++) {
                TextureAtlasSprite sprite = sprites.get(i);
                int tintIndex = tintIndices.get(i);
                boolean shade = shades.get(i);
                boolean ao = aos.get(i);

                int vertexColor;
                if (tintIndex >= 0) {
                    int rawColor = blockColors.getColor(originalState, level, originalPos, tintIndex);
                    int abgr = argbToAbgr(rawColor);
                    vertexColor = forceOpaque(abgr);
                } else {
                    vertexColor = 0xFFFFFFFF;
                }

                BakedQuad newQuad = createQuadFromSource(
                        face,
                        sprite,
                        state.getValue(MicroWorldBlock.PX),
                        state.getValue(MicroWorldBlock.PY),
                        vertexColor,
                        shade,
                        ao
                );
                if (newQuad != null) {
                    resultQuads.add(newQuad);
                }
            }
        }

        return resultQuads;
    }

    // ---- Quad creation ----

    private BakedQuad createQuadFromSource(
            Direction face,
            TextureAtlasSprite sprite,
            int px,
            int py,
            int vertexColor,
            boolean shade,
            boolean ao
    ) {
        if (sprite == null) return null;

        float u0 = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * (px / 16.0f);
        float u1 = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * ((px + 1) / 16.0f);
        float v0 = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * (py / 16.0f);
        float v1 = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * ((py + 1) / 16.0f);

        int[] vertexData = createFullCubeFace(face);

        for (int i = 0; i < 4; i++) {
            int base = i * 8;
            float u, v;
            switch (i) {
                case 0 -> { u = u0; v = v1; }
                case 1 -> { u = u0; v = v0; }
                case 2 -> { u = u1; v = v0; }
                default -> { u = u1; v = v1; }
            }
            vertexData[base + 3] = vertexColor;
            vertexData[base + 4] = Float.floatToIntBits(u);
            vertexData[base + 5] = Float.floatToIntBits(v);
        }

        return new BakedQuad(vertexData, -1, face, sprite, shade, ao);
    }

    private List<BakedQuad> generateFallbackQuads(@Nullable Direction side, TextureAtlasSprite sprite) {
        List<BakedQuad> quads = new ArrayList<>();
        Direction[] faces = (side == null) ? Direction.values() : new Direction[]{side};
        for (Direction face : faces) {
            int[] vertexData = createFullCubeFace(face);
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
                vertexData[base + 3] = 0xFFFFFFFF;
                vertexData[base + 4] = Float.floatToIntBits(u);
                vertexData[base + 5] = Float.floatToIntBits(v);
            }
            quads.add(new BakedQuad(vertexData, -1, face, sprite, true, true));
        }
        return quads;
    }

    private static int[] createFullCubeFace(Direction face) {
        float[][] positions;
        float[] normal;
        switch (face) {
            case DOWN -> {
                positions = new float[][]{
                        {0.0f, 0.0f, 0.0f},
                        {1.0f, 0.0f, 0.0f},
                        {1.0f, 0.0f, 1.0f},
                        {0.0f, 0.0f, 1.0f}
                };
                normal = new float[]{0.0f, -1.0f, 0.0f};
            }
            case UP -> {
                positions = new float[][]{
                        {0.0f, 1.0f, 0.0f},
                        {0.0f, 1.0f, 1.0f},
                        {1.0f, 1.0f, 1.0f},
                        {1.0f, 1.0f, 0.0f}
                };
                normal = new float[]{0.0f, 1.0f, 0.0f};
            }
            case NORTH -> {
                positions = new float[][]{
                        {0.0f, 0.0f, 0.0f},
                        {0.0f, 1.0f, 0.0f},
                        {1.0f, 1.0f, 0.0f},
                        {1.0f, 0.0f, 0.0f}
                };
                normal = new float[]{0.0f, 0.0f, -1.0f};
            }
            case SOUTH -> {
                positions = new float[][]{
                        {0.0f, 0.0f, 1.0f},
                        {1.0f, 0.0f, 1.0f},
                        {1.0f, 1.0f, 1.0f},
                        {0.0f, 1.0f, 1.0f}
                };
                normal = new float[]{0.0f, 0.0f, 1.0f};
            }
            case WEST -> {
                positions = new float[][]{
                        {0.0f, 0.0f, 0.0f},
                        {0.0f, 0.0f, 1.0f},
                        {0.0f, 1.0f, 1.0f},
                        {0.0f, 1.0f, 0.0f}
                };
                normal = new float[]{-1.0f, 0.0f, 0.0f};
            }
            case EAST -> {
                positions = new float[][]{
                        {1.0f, 0.0f, 0.0f},
                        {1.0f, 1.0f, 0.0f},
                        {1.0f, 1.0f, 1.0f},
                        {1.0f, 0.0f, 1.0f}
                };
                normal = new float[]{1.0f, 0.0f, 0.0f};
            }
            default -> throw new IllegalStateException("Unexpected face: " + face);
        }

        int[] data = new int[4 * 8];
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
        // AO smooth-lighting blends each vertex's brightness based on its
        // neighboring blocks' shape, not their actual light level -- so even
        // with light-blocking disabled (MicroWorldBlock#getLightBlock), an
        // invisible neighboring voxel still visually darkens the corners of
        // an adjacent visible one. Since this model is a structure built from
        // thousands of small manufactured cubes rather than a single real
        // block shape, AO's gradient shading doesn't read correctly here
        // regardless -- disable it entirely for flat, uniform lighting.
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