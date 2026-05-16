package justfatlard.conductive_copper.mixin;

import justfatlard.conductive_copper.ConductiveCopper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to propagate redstone updates when a conductive copper block is removed.
 * When a copper block is broken or replaced, the remaining copper network and
 * any adjacent redstone components need to be notified of the topology change.
 */
@Mixin(BlockBehaviour.class)
public class CopperRemovalMixin {

    @Inject(method = "affectNeighborsAfterRemoval", at = @At("TAIL"))
    private void onCopperRemoved(BlockState state, ServerLevel world, BlockPos pos, boolean moved, CallbackInfo ci) {
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
                BlockPos neighborPos = pos.relative(dir);
                world.neighborChanged(neighborPos, Blocks.COPPER_BLOCK, null);
            }
        } finally {
            ConductiveCopper.clearSignalCache();
            ConductiveCopper.IS_PROPAGATING.set(false);
        }
    }
}
