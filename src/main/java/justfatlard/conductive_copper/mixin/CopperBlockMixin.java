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

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Mixin to propagate redstone updates through conductive copper networks.
 * When a copper block receives a neighbor update, it propagates that update
 * to all redstone components touching the copper network.
 */
@Mixin(AbstractBlock.class)
public class CopperBlockMixin {

    @Inject(method = "neighborUpdate", at = @At("HEAD"))
    private void onNeighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, WireOrientation wireOrientation, boolean notify, CallbackInfo ci) {
        if (world.isClient()) {
            return;
        }

        if (!ConductiveCopper.isConductiveCopper(state)) {
            return;
        }

        if (ConductiveCopper.IS_PROPAGATING.get()) {
            return;
        }

        if (ConductiveCopper.isConductiveCopper(sourceBlock)) {
            return;
        }

        try {
            ConductiveCopper.IS_PROPAGATING.set(true);
            ConductiveCopper.clearSignalCache();
            propagateUpdates(world, pos);
        } finally {
            ConductiveCopper.clearSignalCache();
            ConductiveCopper.IS_PROPAGATING.set(false);
        }
    }

    /**
     * Find all copper blocks in the network and update all neighboring
     * redstone components (wires, bulbs, pistons, repeaters, lamps, etc.).
     */
    @Unique
    private void propagateUpdates(World world, BlockPos startPos) {
        Set<BlockPos> visitedCopper = new HashSet<>();
        Set<BlockPos> neighborsToUpdate = new HashSet<>();
        Queue<BlockPos> toVisit = new ArrayDeque<>();

        toVisit.add(startPos);
        visitedCopper.add(startPos);

        while (!toVisit.isEmpty()) {
            if (visitedCopper.size() >= ConductiveCopper.MAX_NETWORK_SIZE) {
                break;
            }

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

                    if (ConductiveCopper.isCopperBulb(neighborState.getBlock())) {
                        neighborsToUpdate.add(neighborPos);
                    }
                } else {
                    // Notify ALL non-copper neighbors: wires, pistons, repeaters, lamps, etc.
                    neighborsToUpdate.add(neighborPos);
                }
            }
        }

        // Also check if the start block itself is a bulb
        BlockState startState = world.getBlockState(startPos);
        if (ConductiveCopper.isCopperBulb(startState.getBlock())) {
            neighborsToUpdate.add(startPos);
        }

        for (BlockPos updatePos : neighborsToUpdate) {
            world.updateNeighbor(updatePos, Blocks.COPPER_BLOCK, null);
        }
    }
}
