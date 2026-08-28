package com.archetipe.microworld.texture;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.awt.Color;
import java.util.*;

public class MaterialPalette {

    private static final Map<String, List<PaletteEntry>> PALETTES =
            new HashMap<>();


    static {

        registerPalette("default",
                new PaletteEntry(Blocks.STONE.defaultBlockState(), new Color(128,128,128)),
                new PaletteEntry(Blocks.DIRT.defaultBlockState(), new Color(134,96,67)),
                new PaletteEntry(Blocks.GRASS_BLOCK.defaultBlockState(), new Color(127,178,56)),
                new PaletteEntry(Blocks.SAND.defaultBlockState(), new Color(218,210,158))
        );


        registerPalette("wood",
                new PaletteEntry(Blocks.OAK_LOG.defaultBlockState(), new Color(102,81,51)),
                new PaletteEntry(Blocks.STRIPPED_OAK_LOG.defaultBlockState(), new Color(180,150,100))
        );


        registerPalette("leaves",
                new PaletteEntry(Blocks.OAK_LEAVES.defaultBlockState(), new Color(80,180,70)),
                new PaletteEntry(Blocks.AZALEA_LEAVES.defaultBlockState(), new Color(100,190,90))
        );


        registerPalette("stone",
                new PaletteEntry(Blocks.STONE.defaultBlockState(), new Color(128,128,128)),
                new PaletteEntry(Blocks.COBBLESTONE.defaultBlockState(), new Color(110,110,110)),
                new PaletteEntry(Blocks.GRAVEL.defaultBlockState(), new Color(136,126,126))
        );


        registerPalette("dirt",

                new PaletteEntry(
                        Blocks.BLACK_TERRACOTTA.defaultBlockState(),
                        new Color(37,22,16)
                ),

                new PaletteEntry(
                        Blocks.GRAY_TERRACOTTA.defaultBlockState(),
                        new Color(57,42,36)
                ),

                new PaletteEntry(
                        Blocks.BROWN_TERRACOTTA.defaultBlockState(),
                        new Color(77,51,36)
                ),

                new PaletteEntry(
                        Blocks.TERRACOTTA.defaultBlockState(),
                        new Color(152,95,69)
                ),

                new PaletteEntry(
                        Blocks.RED_TERRACOTTA.defaultBlockState(),
                        new Color(143,61,47)
                ),

                new PaletteEntry(
                        Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState(),
                        new Color(135,106,97)
                ),

                new PaletteEntry(
                        Blocks.WHITE_TERRACOTTA.defaultBlockState(),
                        new Color(209,177,161)
                ),

                new PaletteEntry(
                        Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(),
                        new Color(126,126,116)
                ),

                new PaletteEntry(
                        Blocks.GRAY_CONCRETE.defaultBlockState(),
                        new Color(55,58,62)
                ),

                new PaletteEntry(
                        Blocks.BROWN_CONCRETE.defaultBlockState(),
                        new Color(96,60,32)
                )
        );


        registerPalette("sand",
                new PaletteEntry(
                        Blocks.SAND.defaultBlockState(),
                        new Color(218,210,158)
                ),
                new PaletteEntry(
                        Blocks.RED_SAND.defaultBlockState(),
                        new Color(190,150,80)
                )
        );


        registerPalette("wool",
                new PaletteEntry(
                        Blocks.WHITE_WOOL.defaultBlockState(),
                        new Color(240,240,240)
                ),
                new PaletteEntry(
                        Blocks.GRAY_WOOL.defaultBlockState(),
                        new Color(80,80,80)
                )
        );
    }


    private static void registerPalette(
            String name,
            PaletteEntry... entries
    ) {
        PALETTES.put(
                name,
                Arrays.asList(entries)
        );
    }


    public static BlockState getClosestBlock(
            Color color,
            String paletteName
    ) {

        List<PaletteEntry> palette =
                PALETTES.getOrDefault(
                        paletteName,
                        PALETTES.get("default")
                );


        PaletteEntry closest = null;
        double closestDistance = Double.MAX_VALUE;


        for (PaletteEntry entry : palette) {

            double distance =
                    colorDistance(
                            color,
                            entry.color
                    );


            if (distance < closestDistance) {

                closestDistance = distance;
                closest = entry;
            }
        }


        return closest.state;
    }



    public static BlockState getClosestBlock(Color color) {
        return getClosestBlock(color, "default");
    }



    private static double colorDistance(
            Color a,
            Color b
    ) {

        double r1 = a.getRed();
        double g1 = a.getGreen();
        double b1 = a.getBlue();

        double r2 = b.getRed();
        double g2 = b.getGreen();
        double b2 = b.getBlue();


        double rDiff = r1 - r2;
        double gDiff = g1 - g2;
        double bDiff = b1 - b2;


        return
                (rDiff * rDiff * 1.5)
                        +
                        (gDiff * gDiff * 2.0)
                        +
                        (bDiff * bDiff * 1.5);
    }



    private record PaletteEntry(
            BlockState state,
            Color color
    ) {}
}