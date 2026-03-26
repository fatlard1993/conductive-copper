package justfatlard.conductive_copper.mixin;

import justfatlard.conductive_copper.ConductiveCopper;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.DefaultRedstoneController;
import net.minecraft.world.World;
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
@Mixin(DefaultRedstoneController.class)
public class RedstoneControllerMixin {

    @Inject(method = "calculateTotalPowerAt", at = @At("RETURN"), cancellable = true)
    private void onCalculateTotalPowerAt(World world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int originalPower = cir.getReturnValue();

        if (originalPower >= 15) {
            return;
        }

        int maxCopperPower = 0;

        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = pos.offset(direction);
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
