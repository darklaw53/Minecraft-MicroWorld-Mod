package com.archetipe.microworld.texture;

import com.mojang.blaze3d.platform.NativeImage;

public final class TextureSampler {

    private TextureSampler() {
    }

    public static int getPixel(
            NativeImage image,
            int x,
            int y
    ) {

        if (x < 0 || y < 0 ||
                x >= image.getWidth() ||
                y >= image.getHeight()) {

            throw new IllegalArgumentException(
                    "Pixel coordinate outside texture bounds"
            );
        }

        return image.getPixelRGBA(x, y);
    }


    public static int getAverageColor(
            NativeImage image
    ) {

        long red = 0;
        long green = 0;
        long blue = 0;
        long count = 0;


        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {

                int color =
                        image.getPixelRGBA(x, y);

                red += color & 0xFF;
                green += (color >> 8) & 0xFF;
                blue += (color >> 16) & 0xFF;

                count++;
            }
        }


        int r = (int) (red / count);
        int g = (int) (green / count);
        int b = (int) (blue / count);


        return r
                | (g << 8)
                | (b << 16);
    }

}