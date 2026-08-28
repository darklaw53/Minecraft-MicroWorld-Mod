package com.archetipe.microworld.texture;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.state.BlockState;

import java.io.InputStreamReader;
import java.util.Optional;

public final class BlockStateResolver {

    private BlockStateResolver() {
    }

    public static ResourceLocation resolve(
            ResourceManager manager,
            BlockState state
    ) throws Exception {

        ResourceLocation blockId =
                state.getBlock()
                        .builtInRegistryHolder()
                        .key()
                        .location();


        ResourceLocation blockstateFile =
                ResourceLocation.fromNamespaceAndPath(
                        blockId.getNamespace(),
                        "blockstates/" +
                                blockId.getPath() +
                                ".json"
                );


        Optional<net.minecraft.server.packs.resources.Resource> resource =
                manager.getResource(blockstateFile);

        if (resource.isEmpty()) {

            manager.listResources(
                    "blockstates",
                    id -> id.getPath().contains("dirt")
            ).forEach((id, res) -> {
                System.out.println("FOUND RESOURCE: " + id);
            });


            throw new IllegalArgumentException(
                    "Could not find: " + blockstateFile
            );
        }

        if (resource.isEmpty()) {

            throw new IllegalArgumentException(
                    "Could not find: " + blockstateFile +
                            "\nNamespace: " + blockId.getNamespace() +
                            "\nPath: " + blockId.getPath()
            );
        }


        if (resource.isEmpty()) {
            throw new IllegalArgumentException(
                    "Missing blockstate: " + blockstateFile
            );
        }


        JsonObject json;

        try (InputStreamReader reader =
                     new InputStreamReader(
                             resource.get().open()
                     )) {

            json = JsonParser.parseReader(reader)
                    .getAsJsonObject();
        }


        JsonObject variants =
                json.getAsJsonObject("variants");


        JsonElement defaultVariant =
                variants.get("");


        if (defaultVariant == null) {
            throw new IllegalArgumentException(
                    "No default variant found for: " + blockId
            );
        }

        JsonObject modelEntry;

        if (defaultVariant.isJsonArray()) {

            modelEntry =
                    defaultVariant
                            .getAsJsonArray()
                            .get(0)
                            .getAsJsonObject();

        } else {

            modelEntry =
                    defaultVariant
                            .getAsJsonObject();
        }

        String model =
                modelEntry
                        .get("model")
                        .getAsString();

        return ResourceLocation.parse(model);
    }

}