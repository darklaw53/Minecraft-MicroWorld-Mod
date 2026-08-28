package com.archetipe.microworld.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class TextureLoader {

    private static final Map<ResourceLocation, NativeImage> CACHE = new HashMap<>();

    private TextureLoader() {
    }

    public static NativeImage load(
            ResourceManager resourceManager,
            ResourceLocation texture
    ) throws IOException {

        NativeImage cached = CACHE.get(texture);

        if (cached != null) {
            return cached;
        }

        ResourceLocation png = ResourceLocation.fromNamespaceAndPath(
                texture.getNamespace(),
                "textures/" + texture.getPath() + ".png"
        );

        Resource resource = resourceManager
                .getResource(png)
                .orElseThrow();

        try (InputStream stream = resource.open()) {

            NativeImage image = NativeImage.read(stream);

            CACHE.put(texture, image);

            return image;
        }
    }

}