package com.archetipe.microworld.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fills the cells of a "hologram" 16x16x16 region that aren't the single
 * MagnifiedBlock, so mobs/players can walk on the far-away illusion. Fully
 * solid, fully invisible, and carries no data of its own -- no block entity,
 * no NBT beyond the default. See assets/microworld/models/block/solid_air.json
 * for the empty render model.
 */
public class SolidAirBlock extends Block {

    public SolidAirBlock(Properties properties) {
        super(properties);
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        // Same reasoning as MicroWorldBlock/MagnifiedBlock: a wall of these
        // stacked 16 deep should not create an enormous dark shadow column
        // just because they're solid for collision purposes.
        return 0;
    }
}