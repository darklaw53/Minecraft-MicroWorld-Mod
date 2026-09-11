package com.archetipe.microworld.dimension;

import com.archetipe.microworld.block.entity.MagnifiedBlockEntity;
import com.archetipe.microworld.registry.ModBlocks;
import com.archetipe.microworld.util.CoordinateMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Replaces MicroWorldPopulationScheduler + MicroWorldPopulationTask
 * entirely. Those filled one whole overworld COLUMN (every Y, ordered
 * closest-to-player-altitude first) before ever starting on a neighboring
 * column -- so the block immediately in front of the player could still be
 * waiting on an entire unrelated column to finish, if that column happened
 * to be dequeued first. Confirmed directly: standing still and peering over
 * an edge, the specific block wanted to stand on kept arriving long after
 * far-away parts of the player's own column, because "own column" was the
 * unit of work, not "physically nearest."
 *
 * This does a true 3D flood fill instead. The unit of BFS traversal is a
 * single overworld block position -- an ("overworld cell") -- and cells are
 * visited in breadth-first order outward from wherever the player actually
 * is, through real neighbors (up/down/north/south/east/west), regardless of
 * which overworld column a given neighbor happens to fall in.
 *
 * TWO-TIER TEST MODE: rather than one bubble, there are now two, both
 * centered on the player but at very different radii and doing very
 * different work:
 *
 *  - OUTER (radius OUTER_RADIUS): fills every cell with SOLID_AIR only --
 *    no MagnifiedBlock, no BlockEntity. This is the "walkable shell" --
 *    real collision/terrain shape for mobs (and the player) to exist on,
 *    kept cheap by never touching the expensive block type. Since
 *    SolidAirBlock has no block entity and no visible faces at all, this
 *    should cost close to what placing plain stone would.
 *
 *  - INNER (radius INNER_RADIUS, much smaller): upgrades the one "master"
 *    micro-position of each already-outer-placed, non-air cell from
 *    SOLID_AIR to MAGNIFIED_BLOCK (+ its BlockEntity) -- this is the
 *    actually-expensive-to-render visual layer, so it's kept to a small
 *    radius on purpose. As the inner bubble moves on, cells that fall
 *    back out of INNER_RADIUS get their master position reverted to
 *    plain SOLID_AIR (not fully cleared -- they're still within the
 *    outer shell, so they should stay solid/walkable, just invisible
 *    again).
 *
 * The one real ordering constraint between the two tiers: a cell can only
 * be inner-upgraded once the OUTER pass has actually finished placing it
 * (see OUTER_COMPLETE below) -- upgrading before that risks the outer
 * pass's own placement later overwriting the magnified block it doesn't
 * know about.
 */
public final class MicroWorldFloodFillScheduler {

    // Tunable: raw setBlock calls per server tick, across however many
    // cells that spans. Confirmed directly: with a whole cell (up to 4096
    // blocks) placed atomically in a single tick, looking toward actively-
    // generating terrain caused visible frame drops (the client has to
    // remesh whatever chunk section just changed, and dumping up to two
    // full sections' worth of changes into one tick produces a real
    // remesh spike) -- while looking away from it was fine, since none of
    // that work was in view. This throttle exists specifically to spread
    // that cost across more, smaller ticks instead, without changing the
    // total amount of work or the BFS order it happens in. Only used when
    // RADIUS_TEST_ENABLED is false (unbounded generation).
    private static final int BLOCKS_PER_TICK = 512;

    // Test mode gets its own, much higher throughput -- a bounded cube
    // can't produce the kind of runaway remesh spike BLOCKS_PER_TICK
    // above exists to prevent, so there's no reason to hold it back the
    // same way. NOTE: OUTER_RADIUS below is now much larger than the
    // original single-bubble test (32 vs 3), meaning a LOT more cells to
    // get through on first teleport or a big move -- this may need
    // retuning once actually measured; solid-air-only placement (no
    // BlockEntity creation) should be cheaper per block than before, but
    // the sheer cell count is roughly (65/7)^3, ~800x more than the
    // original 343-cell test cube.
    private static final int TEST_MODE_BLOCKS_PER_TICK = 65536;

    // Chunk force-loading gets its OWN per-tick budget, entirely separate
    // from the block-placement ones above. Confirmed directly this was
    // missing and is what actually caused a hard server stall ("Can't keep
    // up! Running 2481ms or 49 ticks behind", followed by nothing visibly
    // generating at all beyond ShrinkRayItem's placeholder floor block):
    // microLevel.getChunk(..., ChunkStatus.FULL, true) is a heavy
    // SYNCHRONOUS generation call, and forceLoadAndEnqueue was calling it
    // completely unthrottled. At OUTER_RADIUS=3 that meant at most 7x7=49
    // distinct chunk columns ever needed loading -- fine. At
    // OUTER_RADIUS=32 that's 65x65=4225 distinct columns, ~86x more, and
    // the very first move-triggered reseed tried to force-load a huge
    // wave of them in one go, well before block placement ever got a
    // chance to run. This throttle spreads that cost across many ticks
    // instead, same principle as the block-placement throttles above.
    // Chunks already loaded (the overwhelming majority, once the fill is
    // established) don't count against it -- see forceLoadAndEnqueue.
    private static final int CHUNK_LOADS_PER_TICK = 8;
    private static int chunkLoadsThisTick = 0;

    // TEST MODE. Flip RADIUS_TEST_ENABLED to false to go back to
    // unbounded, single-tier (magnified-everywhere) generation -- that's
    // the only line that needs to change to revert this entirely.
    private static final boolean RADIUS_TEST_ENABLED = true;
    private static final int OUTER_RADIUS = 32;
    private static final int INNER_RADIUS = 6;
    private static BlockPos lastPlayerCell = null;

    // OUTER pass: BFS queue + "seen/queued" dedup, same role these played
    // in the original single-tier design, just now placing solid_air only.
    private static final ArrayDeque<BlockPos> FRONTIER = new ArrayDeque<>();
    private static final Set<Long> VISITED = new HashSet<>();
    // OUTER pass: cells whose solid-air placement has actually finished
    // (or resolved as air/out-of-bounds, i.e. nothing to place) -- this is
    // the gate INNER upgrades wait on, see class doc.
    private static final Set<Long> OUTER_COMPLETE = new HashSet<>();
    // INNER pass: cells whose master position currently holds a
    // MagnifiedBlock rather than plain solid_air.
    private static final Set<Long> INNER_UPGRADED = new HashSet<>();

    // Resumable state for whichever OUTER cell is currently being placed --
    // null when nothing is in progress. Kept as a handful of fields
    // rather than a small record/class purely to avoid an allocation per
    // cell; there's only ever one in-progress cell at a time.
    private static BlockPos inProgressCell = null;
    private static BlockPos inProgressOrigin = null;
    private static int inProgressYStart;
    private static int inProgressYSpan;
    private static int inProgressIndex;

    // Set by ShrinkRayItem immediately before force-loading the destination
    // chunk, for the same reason the old scheduler needed it: that
    // force-load synchronously triggers ChunkEvent.Load (which seeds the
    // flood fill) BEFORE player.teleportTo() actually runs, so the player
    // isn't yet present in this dimension to read an altitude from.
    private static volatile Integer teleportTargetOy = null;

    // Same rationale, but the full overworld cell rather than just Y --
    // needed as a fallback center for the radius test above, for the same
    // "player isn't registered in this dimension yet" window.
    private static volatile BlockPos teleportTargetCell = null;

    private MicroWorldFloodFillScheduler() {}

    public static void setTeleportTargetOy(int overworldY) {
        teleportTargetOy = overworldY;
    }

    public static Integer peekTeleportTargetOy() {
        return teleportTargetOy;
    }

    public static void setTeleportTargetCell(BlockPos overworldCell) {
        teleportTargetCell = overworldCell.immutable();
    }

    /**
     * Clears all state. MUST be called once per server session (see
     * Microworld#onServerStarting) -- every set/field here is static, so
     * without this they silently carry over from one Minecraft "world"
     * into the next whenever both happen to run inside the same JVM
     * session (confirmed directly: a second world created without fully
     * restarting the client inherited the first world's VISITED entries,
     * so every coordinate the flood fill had already "seen" before got
     * skipped, and nothing but ShrinkRayItem's own guaranteed floor block
     * ever appeared). A brand new IntegratedServer is created for every
     * world load though, even within one running client, so hooking that
     * lifecycle event is sufficient.
     */
    public static void reset() {
        FRONTIER.clear();
        VISITED.clear();
        OUTER_COMPLETE.clear();
        INNER_UPGRADED.clear();
        teleportTargetOy = null;
        teleportTargetCell = null;
        lastPlayerCell = null;
        inProgressCell = null;
        inProgressOrigin = null;
    }

    /**
     * Adds a starting point to the OUTER flood fill if it hasn't already
     * been reached -- either by an earlier seed, or by the fill naturally
     * expanding there on its own from some other direction -- and it's
     * within OUTER_RADIUS of the player (see RADIUS_TEST_ENABLED above),
     * if that's currently on. Ordinary BFS-neighbor seeds (organic
     * expansion during processing) go to the back of the queue, same as
     * before -- see seedPriority for the one case that doesn't.
     */
    public static void seed(BlockPos overworldCell) {
        seedInternal(overworldCell, false);
    }

    /**
     * Same as seed(), but jumps straight to the FRONT of FRONTIER instead
     * of the back. Used exclusively by updateAroundPlayer's move-triggered
     * reseed: without this, a freshly-revealed cell right next to the
     * player's CURRENT position would still have to wait behind whatever
     * backlog is left over from wherever they used to be -- with
     * OUTER_RADIUS as large as it now is (32, vs the original 3), that
     * backlog can be substantial, and FRONTIER is a plain FIFO queue with
     * no other notion of "this is more relevant now." This doesn't
     * reorder the backlog itself, just ensures new-since-the-last-move
     * cells don't have to wait behind it.
     */
    private static void seedPriority(BlockPos overworldCell) {
        seedInternal(overworldCell, true);
    }

    private static void seedInternal(BlockPos overworldCell, boolean priority) {
        if (!isWithinOuterRadius(overworldCell)) {
            return;
        }
        if (VISITED.add(overworldCell.asLong())) {
            if (priority) {
                FRONTIER.addFirst(overworldCell.immutable());
            } else {
                FRONTIER.addLast(overworldCell.immutable());
            }
        }
    }

    private static boolean isWithinOuterRadius(BlockPos cell) {
        return withinRadius(cell, OUTER_RADIUS);
    }

    private static boolean isWithinInnerRadius(BlockPos cell) {
        return withinRadius(cell, INNER_RADIUS);
    }

    private static boolean withinRadius(BlockPos cell, int radius) {
        if (!RADIUS_TEST_ENABLED || lastPlayerCell == null) {
            return true;
        }
        return Math.abs(cell.getX() - lastPlayerCell.getX()) <= radius
                && Math.abs(cell.getY() - lastPlayerCell.getY()) <= radius
                && Math.abs(cell.getZ() - lastPlayerCell.getZ()) <= radius;
    }

    /**
     * Re-centers on the player if they've moved to a new overworld cell
     * since the last check: seeds the new OUTER cube, clears whatever
     * fell entirely out of OUTER_RADIUS (full revert to true air -- see
     * clearCell), and reverts whatever fell out of the smaller
     * INNER_RADIUS but is still within the outer shell (revert master
     * position back to solid_air only -- see revertInner). Then, every
     * tick regardless of whether the player moved, retries INNER upgrades
     * for anything now eligible (see attemptInnerUpgrades).
     */
    private static void updateAroundPlayer(ServerLevel microLevel, ServerLevel overworld) {
        if (!RADIUS_TEST_ENABLED) {
            return;
        }

        BlockPos currentCell = resolvePlayerCell(microLevel);
        if (currentCell != null && !currentCell.equals(lastPlayerCell)) {
            BlockPos previousCenter = lastPlayerCell;
            lastPlayerCell = currentCell;

            for (int dx = -OUTER_RADIUS; dx <= OUTER_RADIUS; dx++) {
                for (int dy = -OUTER_RADIUS; dy <= OUTER_RADIUS; dy++) {
                    for (int dz = -OUTER_RADIUS; dz <= OUTER_RADIUS; dz++) {
                        seedPriority(currentCell.offset(dx, dy, dz));
                    }
                }
            }

            if (previousCenter != null) {
                // isWithinOuterRadius/isWithinInnerRadius check against
                // lastPlayerCell, already the NEW center by this point --
                // exactly what we want: "is this old cell still in range
                // of where the player is now."
                for (int dx = -OUTER_RADIUS; dx <= OUTER_RADIUS; dx++) {
                    for (int dy = -OUTER_RADIUS; dy <= OUTER_RADIUS; dy++) {
                        for (int dz = -OUTER_RADIUS; dz <= OUTER_RADIUS; dz++) {
                            BlockPos oldCell = previousCenter.offset(dx, dy, dz);
                            long key = oldCell.asLong();
                            if (!isWithinOuterRadius(oldCell)) {
                                if (VISITED.remove(key)) {
                                    clearCell(microLevel, oldCell);
                                }
                                OUTER_COMPLETE.remove(key);
                                INNER_UPGRADED.remove(key);
                            }
                        }
                    }
                }
                // Smaller pass: cells still within the outer shell but no
                // longer within the inner one. Any overlap with the loop
                // above is harmless -- INNER_UPGRADED.remove() just
                // returns false (already removed) and skips the revert.
                for (int dx = -INNER_RADIUS; dx <= INNER_RADIUS; dx++) {
                    for (int dy = -INNER_RADIUS; dy <= INNER_RADIUS; dy++) {
                        for (int dz = -INNER_RADIUS; dz <= INNER_RADIUS; dz++) {
                            BlockPos oldCell = previousCenter.offset(dx, dy, dz);
                            if (!isWithinInnerRadius(oldCell) && INNER_UPGRADED.remove(oldCell.asLong())) {
                                revertInner(microLevel, oldCell);
                            }
                        }
                    }
                }
            }
        }

        attemptInnerUpgrades(microLevel, overworld);
    }

    /**
     * Runs every tick regardless of whether the player just moved --
     * necessary because a cell can be within INNER_RADIUS well before the
     * OUTER pass has actually finished placing it (OUTER_RADIUS is much
     * bigger, but that doesn't mean it's processed in an order that
     * favors nearby cells first purely because they're nearby -- BFS
     * order depends on when each cell was enqueued). Cheap even when nothing
     * changes: mostly hash-set lookups over at most (2*INNER_RADIUS+1)^3
     * cells.
     */
    private static void attemptInnerUpgrades(ServerLevel microLevel, ServerLevel overworld) {
        if (lastPlayerCell == null) {
            return;
        }

        int scale = CoordinateMapper.SCALE;
        int microMinY = microLevel.getMinBuildHeight();

        for (int dx = -INNER_RADIUS; dx <= INNER_RADIUS; dx++) {
            for (int dy = -INNER_RADIUS; dy <= INNER_RADIUS; dy++) {
                for (int dz = -INNER_RADIUS; dz <= INNER_RADIUS; dz++) {
                    BlockPos cell = lastPlayerCell.offset(dx, dy, dz);
                    long key = cell.asLong();

                    if (INNER_UPGRADED.contains(key) || !OUTER_COMPLETE.contains(key)) {
                        continue;
                    }

                    BlockState sourceState = overworld.getBlockState(cell);
                    if (sourceState.isAir()) {
                        continue;
                    }

                    BlockPos masterPos = masterPosition(cell, scale, microMinY);
                    microLevel.setBlock(masterPos, ModBlocks.MAGNIFIED_BLOCK.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                    BlockEntity be = microLevel.getBlockEntity(masterPos);
                    if (be instanceof MagnifiedBlockEntity magnifiedBE) {
                        magnifiedBE.setOriginalState(sourceState);
                    }
                    INNER_UPGRADED.add(key);
                }
            }
        }
    }

    /** Reverts one cell's master position back to plain solid_air. */
    private static void revertInner(ServerLevel microLevel, BlockPos cell) {
        int scale = CoordinateMapper.SCALE;
        int microMinY = microLevel.getMinBuildHeight();
        BlockPos masterPos = masterPosition(cell, scale, microMinY);
        microLevel.setBlock(masterPos, ModBlocks.SOLID_AIR.get().defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static BlockPos masterPosition(BlockPos cell, int scale, int microMinY) {
        BlockPos origin = CoordinateMapper.toMicroOrigin(cell);
        int clampedYStart = Math.max(cell.getY() * scale, microMinY);
        return new BlockPos(origin.getX(), clampedYStart, origin.getZ());
    }

    /**
     * Reverts one cell's whole scale^3 micro-region back to plain true
     * air (not solid_air -- this is the OUTER cleanup, for cells that
     * left the walkable shell entirely, not just the inner visual
     * radius). Not budget-throttled like placement is -- per player move
     * this only ever touches roughly one face of the outer cube, which is
     * small enough not to need it.
     */
    private static void clearCell(ServerLevel microLevel, BlockPos cell) {
        int scale = CoordinateMapper.SCALE;
        int microMinY = microLevel.getMinBuildHeight();
        int microMaxY = microLevel.getMaxBuildHeight();

        int microYStart = Math.max(cell.getY() * scale, microMinY);
        int microYEnd = Math.min(cell.getY() * scale + scale, microMaxY);
        if (microYEnd <= microYStart) {
            return;
        }

        BlockPos origin = CoordinateMapper.toMicroOrigin(cell);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos microPos = new BlockPos.MutableBlockPos();

        for (int lx = 0; lx < scale; lx++) {
            for (int lz = 0; lz < scale; lz++) {
                for (int my = microYStart; my < microYEnd; my++) {
                    microPos.set(origin.getX() + lx, my, origin.getZ() + lz);
                    microLevel.setBlock(microPos, air, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static BlockPos resolvePlayerCell(ServerLevel microLevel) {
        List<ServerPlayer> players = microLevel.players();
        if (!players.isEmpty()) {
            return CoordinateMapper.toOverworld(players.get(0).blockPosition());
        }
        return teleportTargetCell;
    }

    public static void tick(ServerLevel microLevel, ServerLevel overworld) {
        chunkLoadsThisTick = 0;
        updateAroundPlayer(microLevel, overworld);

        int budget = RADIUS_TEST_ENABLED ? TEST_MODE_BLOCKS_PER_TICK : BLOCKS_PER_TICK;

        while (budget > 0) {
            if (inProgressOrigin == null) {
                if (FRONTIER.isEmpty()) {
                    return;
                }
                startCell(microLevel, overworld, FRONTIER.poll());
                // startCell only sets up the cursor for cells with actual
                // placement work (non-air, in bounds); air/out-of-bounds
                // cells cost nothing and fall through to the next loop
                // iteration immediately.
                continue;
            }

            budget -= continuePlacing(microLevel, budget);
        }
    }

    /**
     * Resolves a newly-dequeued OUTER cell: enqueues its neighbors
     * immediately (BFS order shouldn't wait on how long THIS cell's
     * placement takes), then sets up the resumable placement cursor if
     * there's actually something to place. Marks the cell OUTER_COMPLETE
     * immediately for air/out-of-bounds cells (nothing to place, so
     * nothing to wait on).
     */
    private static void startCell(ServerLevel microLevel, ServerLevel overworld, BlockPos cell) {
        enqueueNeighbors(microLevel, cell);

        int scale = CoordinateMapper.SCALE;
        int microMinY = microLevel.getMinBuildHeight();
        int microMaxY = microLevel.getMaxBuildHeight();

        boolean inOverworldHeight = cell.getY() >= overworld.getMinBuildHeight()
                && cell.getY() < overworld.getMaxBuildHeight();

        int microYStart = cell.getY() * scale;
        int microYEnd = microYStart + scale;
        boolean inMicroHeight = microYStart < microMaxY && microYEnd > microMinY;

        if (!inOverworldHeight || !inMicroHeight || overworld.getBlockState(cell).isAir()) {
            OUTER_COMPLETE.add(cell.asLong());
            return;
        }

        inProgressCell = cell.immutable();
        inProgressOrigin = CoordinateMapper.toMicroOrigin(cell);
        inProgressYStart = Math.max(microYStart, microMinY);
        inProgressYSpan = Math.min(microYEnd, microMaxY) - inProgressYStart;
        inProgressIndex = 0;
    }

    /**
     * Places up to `budget` blocks of the in-progress OUTER cell -- solid
     * air only, for every position, no special-cased master block (that's
     * the INNER pass's job, once this finishes) -- resuming exactly where
     * the last call left off. Marks the cell OUTER_COMPLETE once fully
     * placed. Returns how many blocks were actually placed (== budget
     * unless the cell finished first).
     */
    /**
     * Places up to `budget` blocks of the in-progress OUTER cell -- solid
     * air only, for every position, no special-cased master block (that's
     * the INNER pass's job, once this finishes) -- resuming exactly where
     * the last call left off.
     *
     * ATTEMPTED FAST PATH: writes via ChunkAccess#setBlockState (through
     * the already-loaded LevelChunk) instead of Level#setBlock. This is
     * deliberately the same lower-level API vanilla's own chunk generator
     * uses -- it skips the per-block light-engine recalculation and
     * neighbor-update checks Level#setBlock performs on every call, which
     * is very likely the dominant remaining cost of the old approach
     * (confirmed: solid_air already has getLightBlock()->0, so in
     * principle none of that per-block light work was ever necessary for
     * it in the first place). Client sync is handled once per finished
     * CELL via ChunkMap#resendChunk below, not once per block.
     *
     * REAL CAVEAT, not just a compile risk: solid_air doesn't block light
     * at all, unlike the real terrain it's replacing -- this shell is
     * walkable collision, not a lighting-faithful stand-in, so areas that
     * should be dark (deep underground, say) may not be while only the
     * outer shell exists there. Worth watching for specifically once this
     * is running, separate from whether it compiles.
     *
     * ChunkMap#resendChunk (tried first) did NOT compile against 1.21.1
     * -- confirmed directly. Replaced with Level#sendBlockUpdated per
     * block instead: far more standard/stable API (used throughout
     * vanilla and virtually every mod that touches blocks), so lower
     * compile risk, at the cost of being back to one call per block
     * rather than one per finished cell. Still keeps the actual write on
     * the fast ChunkAccess#setBlockState path -- only the client-notify
     * step changed.
     */
    private static int continuePlacing(ServerLevel microLevel, int budget) {
        int scale = CoordinateMapper.SCALE;
        int total = scale * scale * inProgressYSpan;

        BlockState solidAir = ModBlocks.SOLID_AIR.get().defaultBlockState();
        BlockPos.MutableBlockPos microPos = new BlockPos.MutableBlockPos();

        LevelChunk levelChunk = microLevel.getChunk(
                inProgressOrigin.getX() >> 4, inProgressOrigin.getZ() >> 4);

        int placed = 0;
        while (placed < budget && inProgressIndex < total) {
            int i = inProgressIndex;
            int lx = i / (scale * inProgressYSpan);
            int rem = i % (scale * inProgressYSpan);
            int lz = rem / inProgressYSpan;
            int my = inProgressYStart + (rem % inProgressYSpan);

            microPos.set(inProgressOrigin.getX() + lx, my, inProgressOrigin.getZ() + lz);

            BlockState oldState = levelChunk.setBlockState(microPos, solidAir, false);
            if (oldState == null) {
                oldState = Blocks.AIR.defaultBlockState();
            }
            microLevel.sendBlockUpdated(microPos, oldState, solidAir, Block.UPDATE_CLIENTS);

            inProgressIndex++;
            placed++;
        }

        if (inProgressIndex >= total) {
            OUTER_COMPLETE.add(inProgressCell.asLong());
            inProgressCell = null;
            inProgressOrigin = null;
        }

        return placed;
    }

    private static void enqueueNeighbors(ServerLevel microLevel, BlockPos cell) {
        // Vertical neighbors stay within the same microworld chunk, so
        // there's nothing extra to check -- bounded naturally by the
        // overworld/micro height checks in startCell above.
        seed(cell.above());
        seed(cell.below());

        // Horizontal neighbors can fall in a DIFFERENT overworld column,
        // which is a different microworld chunk. One overworld block X/Z
        // maps 1:1 onto one microworld chunk X/Z (scale == chunk width),
        // so cell.getX()/getZ() here IS the chunk coordinate directly.
        forceLoadAndEnqueue(microLevel, cell.north());
        forceLoadAndEnqueue(microLevel, cell.south());
        forceLoadAndEnqueue(microLevel, cell.east());
        forceLoadAndEnqueue(microLevel, cell.west());
    }

    /**
     * Force-loads the neighboring chunk ourselves (same call ShrinkRayItem
     * already uses for the player's own landing chunk), rather than
     * waiting on vanilla's own view-distance chunk streaming to eventually
     * get around to it. Confirmed directly that waiting was the wrong
     * call: vanilla's own streaming order isn't synchronized with our BFS
     * at all, so whichever direction it happened to load chunks in first
     * is whichever direction the fill could expand into first -- producing
     * a lopsided, direction-biased spread even though the BFS logic itself
     * is symmetric. Forcing it ourselves makes our own queue order the
     * only thing controlling the shape, which is what actually produces a
     * uniform bubble.
     *
     * Only counts against CHUNK_LOADS_PER_TICK when the chunk genuinely
     * isn't loaded yet -- an already-loaded chunk's force-load call is a
     * cheap no-op (just returns the cached chunk), so there's no reason to
     * throttle those; only brand-new generation work is expensive enough
     * to need budgeting.
     */
    private static void forceLoadAndEnqueue(ServerLevel microLevel, BlockPos neighbor) {
        if (!isWithinOuterRadius(neighbor)) {
            // Skip the (comparatively expensive) chunk force-load too --
            // no point loading a chunk purely to have seed() reject it.
            return;
        }

        boolean alreadyLoaded = microLevel.hasChunk(neighbor.getX(), neighbor.getZ());
        if (!alreadyLoaded) {
            if (chunkLoadsThisTick >= CHUNK_LOADS_PER_TICK) {
                // Budget exhausted for this tick -- skip for now. This
                // exact neighbor gets retried the next time some other
                // already-loaded cell's own neighbor-enqueue reaches it,
                // or the next time the player moves and
                // updateAroundPlayer reseeds the whole outer cube.
                return;
            }
            chunkLoadsThisTick++;
        }

        microLevel.getChunk(neighbor.getX(), neighbor.getZ(), ChunkStatus.FULL, true);
        seed(neighbor);
    }
}