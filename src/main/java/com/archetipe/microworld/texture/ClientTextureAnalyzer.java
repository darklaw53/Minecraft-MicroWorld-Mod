package com.archetipe.microworld.texture;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

@OnlyIn(Dist.CLIENT)
public final class ClientTextureAnalyzer {

    private ClientTextureAnalyzer() {
    }

    private static void savePaletteEntry(
            String blockName,
            int r,
            int g,
            int b
    ) {

        File file =
                new File(
                        "microworld_palette.txt"
                );


        try {

            if (file.exists()) {

                Scanner scanner =
                        new Scanner(file);


                while (scanner.hasNextLine()) {

                    String line =
                            scanner.nextLine();


                    if (line.startsWith(blockName + " =")) {

                        scanner.close();
                        return;
                    }
                }


                scanner.close();
            }


            FileWriter writer =
                    new FileWriter(
                            file,
                            true
                    );


            writer.write(
                    blockName
                            + " = "
                            + r
                            + ","
                            + g
                            + ","
                            + b
                            + "\n"
            );


            writer.close();


        } catch (IOException e) {

            e.printStackTrace();

        }
    }


    public static void analyze(BlockPos pos) {

        Minecraft minecraft =
                Minecraft.getInstance();


        if (minecraft.level == null ||
                minecraft.player == null) {
            return;
        }


        BlockState state =
                minecraft.level.getBlockState(pos);


        try {

            ResourceLocation model =
                    BlockStateResolver.resolve(
                            minecraft.getResourceManager(),
                            state
                    );


            ResolvedModel resolved =
                    ModelResolver.resolve(
                            minecraft.getResourceManager(),
                            model
                    );


            ResourceLocation texture =
                    resolved.top()
                            .getTexture();


            var image =
                    TextureLoader.load(
                            minecraft.getResourceManager(),
                            texture
                    );


            int width = image.getWidth();
            int height = image.getHeight();


            minecraft.player.sendSystemMessage(
                    Component.literal(
                            "Texture Size: "
                                    + width
                                    + "x"
                                    + height
                    )
            );


            int[][] samples = {
                    {0, 0},
                    {width / 2, height / 2},
                    {width - 1, height - 1}
            };


            String blockName =
                    state.getBlock()
                            .builtInRegistryHolder()
                            .key()
                            .location()
                            .toString();


            for (int[] sample : samples) {

                int x = sample[0];
                int y = sample[1];


                int color =
                        image.getPixelRGBA(
                                x,
                                y
                        );


                int r = color & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = (color >> 16) & 0xFF;


                savePaletteEntry(
                        blockName,
                        r,
                        g,
                        b
                );


                minecraft.player.sendSystemMessage(
                        Component.literal(
                                "Pixel "
                                        + x
                                        + ","
                                        + y
                                        + ": RGB("
                                        + r
                                        + ", "
                                        + g
                                        + ", "
                                        + b
                                        + ")"
                        )
                );
            }


            int average =
                    TextureSampler.getAverageColor(
                            image
                    );


            int r = average & 0xFF;
            int g = (average >> 8) & 0xFF;
            int b = (average >> 16) & 0xFF;


            minecraft.player.sendSystemMessage(
                    Component.literal(
                            "Block: "
                                    + state.getBlock()
                                    .getName()
                                    .getString()
                    )
            );


            minecraft.player.sendSystemMessage(
                    Component.literal(
                            "Model: "
                                    + model
                    )
            );


            minecraft.player.sendSystemMessage(
                    Component.literal(
                            "Texture: "
                                    + texture
                    )
            );


            minecraft.player.sendSystemMessage(
                    Component.literal(
                            "Average RGB: "
                                    + r
                                    + ", "
                                    + g
                                    + ", "
                                    + b
                    )
            );


        } catch (Exception e) {

            e.printStackTrace();

            minecraft.player.sendSystemMessage(
                    Component.literal(
                            "Texture analysis failed: "
                                    + e.getMessage()
                    )
            );
        }
    }
}