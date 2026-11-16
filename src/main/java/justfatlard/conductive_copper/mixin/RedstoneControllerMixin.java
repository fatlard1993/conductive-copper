package justfatlard.conductive_copper.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import justfatlard.conductive_copper.ConductiveCopper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.DefaultRedstoneWireEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Makes redstone wire receive signals through conductive copper: injects into
 * the wire evaluator's calculateTargetStrength to add copper-conducted power.
 *
 * Known gap: worlds with the REDSTONE_EXPERIMENTS feature flag enabled use
 * ExperimentalRedstoneWireEvaluator instead (see RedStoneWireBlock.useExperimentalEvaluator),
 * which this mixin does not hook. Wire-adjacent conduction degrades on such worlds;
 * default worlds are unaffected.
 */
@Mixin(DefaultRedstoneWireEvaluator.class)
public class RedstoneControllerMixin {

    @ModifyReturnValue(method = "calculateTargetStrength", at = @At("RETURN"))
    private int onCalculateTotalPowerAt(int originalPower, Level world, BlockPos pos) {
        if (originalPower >= 15) {
            return originalPower;
        }

        int maxCopperPower = 0;

        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = pos.relative(direction);
            BlockState adjacentState = world.getBlockState(adjacentPos);

            if (ConductiveCopper.isConductor(adjacentState)) {
                int copperSignal = ConductiveCopper.getSignalThroughCopper(
                    world,
                    adjacentPos,
                    direction.getOpposite()
                );
                maxCopperPower = Math.max(maxCopperPower, copperSignal);
            }
        }

        return Math.max(maxCopperPower, originalPower);
    }
}
