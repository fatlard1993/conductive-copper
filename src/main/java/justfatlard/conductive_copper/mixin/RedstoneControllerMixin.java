package justfatlard.conductive_copper.mixin;

import justfatlard.conductive_copper.ConductiveCopper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.DefaultRedstoneWireEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to make redstone wire receive signals through conductive copper blocks.
 *
 * The redstone system uses DefaultRedstoneController.calculateTotalPowerAt()
 * which gets strong power and wire power. We inject to add copper-conducted power.
 */
@Mixin(DefaultRedstoneWireEvaluator.class)
public class RedstoneControllerMixin {

    @Inject(method = "calculateTargetStrength", at = @At("RETURN"), cancellable = true)
    private void onCalculateTotalPowerAt(Level world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int originalPower = cir.getReturnValue();

        if (originalPower >= 15) {
            return;
        }

        int maxCopperPower = 0;

        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = pos.relative(direction);
            BlockState adjacentState = world.getBlockState(adjacentPos);

            if (ConductiveCopper.isConductiveCopper(adjacentState)) {
                int copperSignal = ConductiveCopper.getSignalThroughCopper(
                    world,
                    adjacentPos,
                    direction.getOpposite()
                );
                maxCopperPower = Math.max(maxCopperPower, copperSignal);
            }
        }

        if (maxCopperPower > originalPower) {
            cir.setReturnValue(maxCopperPower);
        }
    }
}
