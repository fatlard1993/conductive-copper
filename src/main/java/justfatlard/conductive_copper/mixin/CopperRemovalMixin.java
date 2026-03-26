package justfatlard.conductive_copper.mixin;

import justfatlard.conductive_copper.ConductiveCopper;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to propagate redstone updates when a conductive copper block is removed.
 * When a copper block is broken or replaced, the remaining copper network and
 * any adjacent redstone components need to be notified of the topology change.
 */
@Mixin(AbstractBlock.class)
public class CopperRemovalMixin {

    @Inject(method = "onStateReplaced", at = @At("TAIL"))
    private void onCopperRemoved(BlockState state, ServerWorld world, BlockPos pos, boolean moved, CallbackInfo ci) {
        if (!ConductiveCopper.isConductiveCopper(state)) {
            return;
        }

        if (ConductiveCopper.IS_PROPAGATING.get()) {
            return;
        }

        try {
            ConductiveCopper.IS_PROPAGATING.set(true);
            ConductiveCopper.clearSignalCache();

            // Notify all neighbors of the removed copper block so adjacent
            // copper networks and redstone components can recalculate
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = pos.offset(dir);
                world.updateNeighbor(neighborPos, Blocks.COPPER_BLOCK, null);
            }
        } finally {
            ConductiveCopper.clearSignalCache();
            ConductiveCopper.IS_PROPAGATING.set(false);
        }
    }
}
