package com.archetipe.microworld.texture;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.util.Map;
import java.util.Optional;

public final class ModelLoader {

    private static final Gson GSON = new Gson();

    private ModelLoader() {
    }

    public static BlockModelData load(
            ResourceManager manager,
            ResourceLocation model
    ) throws Exception {

        ResourceLocation jsonLocation =
                ResourceLocation.fromNamespaceAndPath(
                        model.getNamespace(),
                        "models/" + model.getPath() + ".json"
                );

        Optional<Resource> optional =
                manager.getResource(jsonLocation);


        if (optional.isEmpty()) {

            jsonLocation =
                    ResourceLocation.fromNamespaceAndPath(
                            model.getNamespace(),
                            "models/block/" + model.getPath() + ".json"
                    );

            optional =
                    manager.getResource(jsonLocation);
        }


        if (optional.isEmpty()) {
            throw new IllegalArgumentException(
                    "Missing model: " + jsonLocation
            );
        }


        JsonObject json;

        try (InputStreamReader reader =
                     new InputStreamReader(optional.get().open())) {

            json = GSON.fromJson(
                    reader,
                    JsonObject.class
            );
        }


        BlockModelData result =
                new BlockModelData();


        if (json.has("parent")) {

            result.setParent(
                    json.get("parent")
                            .getAsString()
            );
        }


        if (json.has("textures")) {

            JsonObject textures =
                    json.getAsJsonObject("textures");

            for (Map.Entry<String, JsonElement> entry :
                    textures.entrySet()) {

                result.getTextures().put(
                        entry.getKey(),
                        entry.getValue().getAsString()
                );
            }
        }


        if (json.has("elements")) {

            JsonArray elements =
                    json.getAsJsonArray("elements");


            for (JsonElement elementEntry : elements) {

                JsonObject element =
                        elementEntry.getAsJsonObject();


                if (!element.has("faces")) {
                    continue;
                }


                JsonObject faces =
                        element.getAsJsonObject("faces");


                for (Map.Entry<String, JsonElement> faceEntry :
                        faces.entrySet()) {

                    Direction direction =
                            parseDirection(faceEntry.getKey());


                    if (direction == null) {
                        continue;
                    }


                    JsonObject face =
                            faceEntry.getValue()
                                    .getAsJsonObject();


                    String texture =
                            face.get("texture")
                                    .getAsString();


                    int rotation =
                            face.has("rotation")
                                    ? face.get("rotation")
                                    .getAsInt()
                                    : 0;


                    float[] uv = null;


                    if (face.has("uv")) {

                        JsonArray uvArray =
                                face.getAsJsonArray("uv");


                        uv = new float[]{
                                uvArray.get(0).getAsFloat(),
                                uvArray.get(1).getAsFloat(),
                                uvArray.get(2).getAsFloat(),
                                uvArray.get(3).getAsFloat()
                        };
                    }


                    result.addFace(
                            direction,
                            new ModelFace(
                                    texture,
                                    rotation,
                                    uv
                            )
                    );
                }
            }
        }


        return result;
    }


    private static Direction parseDirection(String name) {

        return switch (name) {

            case "up" -> Direction.UP;
            case "down" -> Direction.DOWN;
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;

            default -> null;
        };
    }
}