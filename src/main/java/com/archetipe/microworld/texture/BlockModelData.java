package com.archetipe.microworld.texture;

import net.minecraft.core.Direction;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class BlockModelData {

    private String parent;

    private final Map<String, String> textures = new HashMap<>();

    private final Map<Direction, ModelFace> faces =
            new EnumMap<>(Direction.class);

    public String getParent() {
        return parent;
    }

    public void setParent(String parent) {
        this.parent = parent;
    }

    public Map<String, String> getTextures() {
        return textures;
    }

    public Map<Direction, ModelFace> getFaces() {
        return faces;
    }

    public void addFace(
            Direction direction,
            ModelFace face
    ) {
        faces.put(direction, face);
    }
}