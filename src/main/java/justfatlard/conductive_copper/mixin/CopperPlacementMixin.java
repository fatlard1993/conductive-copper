package justfatlard.conductive_copper.mixin;

import justfatlard.conductive_copper.QuietNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells QuietNetworks about every block change the server makes, through the one call every
 * change goes through whatever the block, so a conductor or a source arriving is never missed.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public class CopperPlacementMixin {

    @Inject(method = "onPlace", at = @At("HEAD"))
    private void onBlockChanged(Level world, BlockPos pos, BlockState oldState, boolean movedByPiston, CallbackInfo ci) {
        QuietNetworks.changed(world, (BlockState)(Object)this, oldState);
    }
}
