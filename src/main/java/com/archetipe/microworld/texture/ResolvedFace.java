package com.archetipe.microworld.texture;

import net.minecraft.resources.ResourceLocation;

public class ResolvedFace {

    private final ResourceLocation texture;

    private final int rotation;

    private final float[] uv;

    public ResolvedFace(
            ResourceLocation texture,
            int rotation,
            float[] uv
    ) {
        this.texture = texture;
        this.rotation = rotation;
        this.uv = uv;
    }

    public ResourceLocation getTexture() {
        return texture;
    }

    public int getRotation() {
        return rotation;
    }

    public float[] getUv() {
        return uv;
    }

    public boolean hasCustomUV() {
        return uv != null;
    }
}