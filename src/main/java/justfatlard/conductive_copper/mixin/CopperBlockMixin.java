package justfatlard.conductive_copper.mixin;

import justfatlard.conductive_copper.ConductiveCopper;
import justfatlard.conductive_copper.QuietNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
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
 * to every block touching the copper network that listens for one, unless the
 * network is already known to carry nothing (see QuietNetworks).
 */
@Mixin(BlockBehaviour.class)
public class CopperBlockMixin {

    @Inject(method = "neighborChanged", at = @At("HEAD"))
    private void onNeighborUpdate(BlockState state, Level world, BlockPos pos, Block sourceBlock, Orientation wireOrientation, boolean notify, CallbackInfo ci) {
        if (world.isClientSide()) {
            return;
        }

        if (!ConductiveCopper.isConductor(state)) {
            return;
        }

        if (ConductiveCopper.IS_PROPAGATING.get()) {
            return;
        }

        if (ConductiveCopper.isConductor(sourceBlock)) {
            return;
        }

        try {
            ConductiveCopper.IS_PROPAGATING.set(true);
            ConductiveCopper.clearSignalCache();
            if (!QuietNetworks.stillQuiet(world, pos)) {
                propagateUpdates(world, pos);
            }
        } finally {
            ConductiveCopper.clearSignalCache();
            ConductiveCopper.IS_PROPAGATING.set(false);
        }
    }

    @Unique
    private void propagateUpdates(Level world, BlockPos startPos) {
        Set<BlockPos> visitedCopper = new HashSet<>();
        Set<BlockPos> neighborsToUpdate = new HashSet<>();
        Queue<BlockPos> toVisit = new ArrayDeque<>();
        boolean powered = false;
        boolean whole = true;

        toVisit.add(startPos);
        visitedCopper.add(startPos);

        while (!toVisit.isEmpty()) {
            if (visitedCopper.size() >= ConductiveCopper.MAX_NETWORK_SIZE) {
                whole = false;
                break;
            }

            BlockPos current = toVisit.poll();

            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = current.relative(dir);

                if (visitedCopper.contains(neighborPos)) {
                    continue;
                }

                BlockState neighborState = world.getBlockState(neighborPos);

                if (ConductiveCopper.isConductor(neighborState)) {
                    visitedCopper.add(neighborPos);
                    toVisit.add(neighborPos);

                    if (ConductiveCopper.isCopperBulb(neighborState.getBlock())) {
                        neighborsToUpdate.add(neighborPos);
                    }
                } else {
                    // Every non-copper neighbour is collected, listening or not, and the ones
                    // that do not listen are passed over only below: the order the rest are told
                    // in follows this set's iteration, which hangs on everything in it.
                    neighborsToUpdate.add(neighborPos);
                    powered |= QuietNetworks.isSource(neighborState);
                }
            }
        }

        BlockState startState = world.getBlockState(startPos);
        if (ConductiveCopper.isCopperBulb(startState.getBlock())) {
            neighborsToUpdate.add(startPos);
        }

        for (BlockPos updatePos : neighborsToUpdate) {
            if (ConductiveCopper.reactsToNeighbours(world.getBlockState(updatePos))) {
                world.neighborChanged(updatePos, Blocks.COPPER_BLOCK.weathering().unaffected(), null);
            }
        }

        QuietNetworks.walked(world, visitedCopper, whole && !powered);
    }
}
