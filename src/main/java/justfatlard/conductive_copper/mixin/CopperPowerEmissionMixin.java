package justfatlard.conductive_copper.mixin;

import justfatlard.conductive_copper.ConductiveCopper;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to make conductive copper blocks emit weak redstone power
 * when they're part of a powered copper network.
 *
 * This allows redstone components (like copper bulbs) that are adjacent
 * to the copper network to detect the conducted power.
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public class CopperPowerEmissionMixin {

    // Recursion guard to prevent infinite loops when checking power
    @Unique
    private static final ThreadLocal<Boolean> IS_CHECKING_COPPER_POWER = ThreadLocal.withInitial(() -> false);

    /**
     * Override getWeakRedstonePower for conductive copper blocks.
     * Returns the power level conducted through the copper network.
     */
    @Inject(method = "getWeakRedstonePower", at = @At("HEAD"), cancellable = true)
    private void onGetWeakRedstonePower(BlockView world, BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> cir) {
        // Prevent recursion - if we're already checking copper power, don't recurse
        if (IS_CHECKING_COPPER_POWER.get()) {
            return;
        }

        // Get the BlockState from the mixin target
        BlockState state = (BlockState)(Object)this;

        // Only process conductive copper blocks
        if (!ConductiveCopper.isConductiveCopper(state)) {
            return;
        }

        // Need a World instance for the full BFS search
        // BlockView might not be a World, so we need to check
        if (!(world instanceof net.minecraft.world.World)) {
            return;
        }

        net.minecraft.world.World worldInstance = (net.minecraft.world.World) world;

        try {
            IS_CHECKING_COPPER_POWER.set(true);

            // Get the signal conducted through this copper network
            // The direction parameter is the direction FROM which power is being queried
            int power = ConductiveCopper.getSignalThroughCopper(worldInstance, pos, direction);

            if (power > 0) {
                cir.setReturnValue(power);
            }
        } finally {
            IS_CHECKING_COPPER_POWER.set(false);
        }
    }
}
