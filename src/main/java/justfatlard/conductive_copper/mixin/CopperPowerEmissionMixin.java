package justfatlard.conductive_copper.mixin;

import justfatlard.conductive_copper.ConductiveCopper;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
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

    @Inject(method = "getWeakRedstonePower", at = @At("HEAD"), cancellable = true)
    private void onGetWeakRedstonePower(BlockView world, BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> cir) {
        if (ConductiveCopper.IS_CHECKING_COPPER_POWER.get()) {
            return;
        }

        BlockState state = (BlockState)(Object)this;

        if (!ConductiveCopper.isConductiveCopper(state)) {
            return;
        }

        if (!(world instanceof net.minecraft.world.World worldInstance)) {
            return;
        }

        try {
            ConductiveCopper.IS_CHECKING_COPPER_POWER.set(true);

            int power = ConductiveCopper.getSignalThroughCopper(worldInstance, pos, direction);

            if (power > 0) {
                cir.setReturnValue(power);
            }
        } finally {
            ConductiveCopper.IS_CHECKING_COPPER_POWER.set(false);
        }
    }
}
