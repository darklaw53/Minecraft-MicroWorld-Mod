package com.archetipe.microworld.texture;

public class ModelFace {

    private final String texture;

    private final int rotation;

    private final float[] uv;

    public ModelFace(
            String texture,
            int rotation,
            float[] uv
    ) {
        this.texture = texture;
        this.rotation = rotation;
        this.uv = uv;
    }

    public String getTexture() {
        return texture;
    }

    public int getRotation() {
        return rotation;
    }

    public float[] getUv() {
        return uv;
    }
}