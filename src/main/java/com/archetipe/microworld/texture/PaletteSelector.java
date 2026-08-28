package com.archetipe.microworld.texture;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

public class PaletteSelector {

    /**
     * Chooses which material palette should be used
     * when converting a block texture into a micro structure.
     */
    public static String getPalette(BlockState blockState) {

        if (blockState.is(BlockTags.LOGS)) {
            return "wood";
        }


        if (blockState.is(BlockTags.LEAVES)) {
            return "leaves";
        }


        if (blockState.is(BlockTags.BASE_STONE_OVERWORLD)) {
            return "stone";
        }


        if (blockState.is(BlockTags.DIRT)) {
            return "dirt";
        }


        if (blockState.is(BlockTags.SAND)) {
            return "sand";
        }


        if (blockState.is(BlockTags.WOOL)) {
            return "wool";
        }


        return "default";
    }


    private PaletteSelector() {
    }
}