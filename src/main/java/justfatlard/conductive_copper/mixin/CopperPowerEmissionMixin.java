package justfatlard.conductive_copper.mixin;

import justfatlard.conductive_copper.ConductiveCopper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes conductive copper blocks emit weak redstone power when they're part
 * of a powered copper network, so adjacent components can detect it.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public class CopperPowerEmissionMixin {

    @Inject(method = "getSignal", at = @At("HEAD"), cancellable = true)
    private void onGetWeakRedstonePower(BlockGetter world, BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> cir) {
        if (ConductiveCopper.IS_CHECKING_COPPER_POWER.get()) {
            return;
        }

        BlockState state = (BlockState)(Object)this;

        if (!ConductiveCopper.isConductiveCopper(state)) {
            return;
        }

        if (!(world instanceof net.minecraft.world.level.Level worldInstance)) {
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
