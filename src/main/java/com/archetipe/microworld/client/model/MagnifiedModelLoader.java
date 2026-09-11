package com.archetipe.microworld.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

import java.util.function.Function;

public class MagnifiedModelLoader implements IGeometryLoader<MagnifiedModelLoader.MagnifiedUnbakedModel> {

    @Override
    public MagnifiedUnbakedModel read(JsonObject json, JsonDeserializationContext context) {
        BlockModel baseModel = context.deserialize(json.get("base_model"), BlockModel.class);
        return new MagnifiedUnbakedModel(baseModel);
    }

    public static class MagnifiedUnbakedModel implements IUnbakedGeometry<MagnifiedUnbakedModel> {
        private final BlockModel baseModel;

        public MagnifiedUnbakedModel(BlockModel baseModel) {
            this.baseModel = baseModel;
        }

        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides) {
            BakedModel baseBaked = baseModel.bake(baker, spriteGetter, modelState);
            return new MagnifiedBakedModel(baseBaked);
        }
    }
}