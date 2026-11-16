package justfatlard.conductive_copper.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import justfatlard.conductive_copper.ConductiveCopper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Makes conductive copper blocks emit weak redstone power when they're part
 * of a powered copper network, so adjacent components can detect it.
 *
 * <p>Every redstone read in the game passes through here, so the conductor test comes first and
 * nothing is allocated for a block that is not one.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public class CopperPowerEmissionMixin {

    @ModifyReturnValue(method = "getSignal", at = @At("RETURN"))
    private int onGetWeakRedstonePower(int original, BlockGetter world, BlockPos pos, Direction direction) {
        if (!ConductiveCopper.isConductor((BlockState)(Object)this)) {
            return original;
        }

        if (!(world instanceof Level worldInstance)) {
            return original;
        }

        if (ConductiveCopper.IS_CHECKING_COPPER_POWER.get()) {
            return original;
        }

        try {
            ConductiveCopper.IS_CHECKING_COPPER_POWER.set(true);

            int power = ConductiveCopper.getSignalThroughCopper(worldInstance, pos, direction);
            return power > 0 ? power : original;
        } finally {
            ConductiveCopper.IS_CHECKING_COPPER_POWER.set(false);
        }
    }
}
