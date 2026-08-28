package com.archetipe.microworld.texture;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class ModelResolver {

    private ModelResolver() {
    }

    public static ResolvedModel resolve(
            ResourceManager manager,
            ResourceLocation model
    ) throws Exception {

        Map<String, String> textures = new HashMap<>();
        Map<Direction, ModelFace> faces = new EnumMap<>(Direction.class);

        collectModelData(
                manager,
                model,
                textures,
                faces
        );

        return createResolvedModel(
                textures,
                faces
        );
    }


    private static void collectModelData(
            ResourceManager manager,
            ResourceLocation model,
            Map<String, String> textures,
            Map<Direction, ModelFace> faces
    ) throws Exception {

        BlockModelData data =
                ModelLoader.load(
                        manager,
                        model
                );


        if (data.getParent() != null) {

            collectModelData(
                    manager,
                    ResourceLocation.parse(
                            data.getParent()
                    ),
                    textures,
                    faces
            );
        }


        textures.putAll(
                data.getTextures()
        );


        for (Map.Entry<Direction, ModelFace> entry :
                data.getFaces().entrySet()) {

            faces.put(
                    entry.getKey(),
                    entry.getValue()
            );
        }
    }


    private static ResolvedModel createResolvedModel(
            Map<String, String> textures,
            Map<Direction, ModelFace> faces
    ) {

        return new ResolvedModel(

                resolveFace(
                        textures,
                        faces,
                        Direction.UP
                ),

                resolveFace(
                        textures,
                        faces,
                        Direction.DOWN
                ),

                resolveFace(
                        textures,
                        faces,
                        Direction.NORTH
                ),

                resolveFace(
                        textures,
                        faces,
                        Direction.SOUTH
                ),

                resolveFace(
                        textures,
                        faces,
                        Direction.EAST
                ),

                resolveFace(
                        textures,
                        faces,
                        Direction.WEST
                )
        );
    }


    private static ResolvedFace resolveFace(
            Map<String, String> textures,
            Map<Direction, ModelFace> faces,
            Direction direction
    ) {

        ModelFace face =
                faces.get(direction);


        if (face == null) {
            return null;
        }


        return new ResolvedFace(

                resolveTexture(
                        textures,
                        face.getTexture()
                ),

                face.getRotation(),

                face.getUv()
        );
    }


    private static ResourceLocation resolveTexture(
            Map<String, String> textures,
            String key
    ) {

        String value = key;


        while (value.startsWith("#")) {

            value =
                    textures.get(
                            value.substring(1)
                    );

            if (value == null) {
                return null;
            }
        }


        return ResourceLocation.parse(value);
    }
}