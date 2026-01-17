package justfatlard.conductive_copper.mixin;

import justfatlard.conductive_copper.ConductiveCopper;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * Mixin to propagate redstone updates through conductive copper networks.
 * When a copper block receives a neighbor update, it propagates that update
 * to all redstone components touching the copper network.
 */
@Mixin(AbstractBlock.class)
public class CopperBlockMixin {

    @Unique
    private static final ThreadLocal<Boolean> IS_PROPAGATING = ThreadLocal.withInitial(() -> false);

    @Unique
    private static final Set<Block> COPPER_BULBS = Set.of(
        Blocks.COPPER_BULB, Blocks.EXPOSED_COPPER_BULB,
        Blocks.WEATHERED_COPPER_BULB, Blocks.OXIDIZED_COPPER_BULB,
        Blocks.WAXED_COPPER_BULB, Blocks.WAXED_EXPOSED_COPPER_BULB,
        Blocks.WAXED_WEATHERED_COPPER_BULB, Blocks.WAXED_OXIDIZED_COPPER_BULB
    );

    /**
     * When a copper block is removed, update all adjacent redstone wires.
     */
    @Inject(method = "onStateReplaced", at = @At("HEAD"))
    private void onCopperRemoved(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved, CallbackInfo ci) {
        if (world.isClient()) {
            return;
        }

        if (!ConductiveCopper.isConductiveCopper(state)) {
            return;
        }
        if (ConductiveCopper.isConductiveCopper(newState)) {
            return;
        }

        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.offset(dir);
            BlockState neighborState = world.getBlockState(neighborPos);

            if (neighborState.getBlock() == Blocks.REDSTONE_WIRE) {
                world.updateNeighbor(neighborPos, Blocks.COPPER_BLOCK, null);
            } else if (ConductiveCopper.isConductiveCopper(neighborState)) {
                world.updateNeighbor(neighborPos, Blocks.AIR, null);
            }
        }
    }

    @Inject(method = "neighborUpdate", at = @At("HEAD"))
    private void onNeighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, WireOrientation wireOrientation, boolean notify, CallbackInfo ci) {
        if (world.isClient()) {
            return;
        }

        if (!ConductiveCopper.isConductiveCopper(state)) {
            return;
        }

        if (IS_PROPAGATING.get()) {
            return;
        }

        if (ConductiveCopper.isConductiveCopper(sourceBlock.getDefaultState())) {
            return;
        }

        try {
            IS_PROPAGATING.set(true);
            propagateUpdates(world, pos);
        } finally {
            IS_PROPAGATING.set(false);
        }
    }

    /**
     * Find all copper blocks in the network and update all redstone wires and bulbs touching them.
     */
    @Unique
    private void propagateUpdates(World world, BlockPos startPos) {
        Set<BlockPos> visitedCopper = new HashSet<>();
        Set<BlockPos> redstoneToUpdate = new HashSet<>();
        Set<BlockPos> bulbsToUpdate = new HashSet<>();
        Queue<BlockPos> toVisit = new LinkedList<>();

        toVisit.add(startPos);
        visitedCopper.add(startPos);

        while (!toVisit.isEmpty()) {
            BlockPos current = toVisit.poll();

            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = current.offset(dir);

                if (visitedCopper.contains(neighborPos)) {
                    continue;
                }

                BlockState neighborState = world.getBlockState(neighborPos);

                if (ConductiveCopper.isConductiveCopper(neighborState)) {
                    visitedCopper.add(neighborPos);
                    toVisit.add(neighborPos);

                    if (COPPER_BULBS.contains(neighborState.getBlock())) {
                        bulbsToUpdate.add(neighborPos);
                    }
                } else if (neighborState.getBlock() == Blocks.REDSTONE_WIRE) {
                    redstoneToUpdate.add(neighborPos);
                }
            }
        }

        BlockState startState = world.getBlockState(startPos);
        if (COPPER_BULBS.contains(startState.getBlock())) {
            bulbsToUpdate.add(startPos);
        }

        for (BlockPos wirePos : redstoneToUpdate) {
            world.updateNeighbor(wirePos, Blocks.COPPER_BLOCK, null);
        }

        for (BlockPos bulbPos : bulbsToUpdate) {
            world.updateNeighbor(bulbPos, Blocks.COPPER_BLOCK, null);
        }
    }
}
