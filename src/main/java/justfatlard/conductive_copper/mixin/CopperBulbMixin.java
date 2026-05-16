package justfatlard.conductive_copper.mixin;

import justfatlard.conductive_copper.ConductiveCopper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.redstone.Orientation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

/**
 * Mixin to make copper bulbs toggle their light when receiving copper-conducted power.
 * Mimics vanilla toggle behavior: LIT toggles on rising edge (unpowered -> powered).
 *
 * With CopperPowerEmissionMixin active, copper blocks report power through vanilla's
 * getWeakRedstonePower API. This mixin uses world.isReceivingRedstonePower() to get
 * the combined vanilla + copper emission power.
 */
@Mixin(CopperBulbBlock.class)
public class CopperBulbMixin {

    // Per-position guard so Bulb A toggling doesn't suppress Bulb B's update
    @Unique
    private static final ThreadLocal<Set<BlockPos>> IS_UPDATING = ThreadLocal.withInitial(HashSet::new);

    @Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true)
    private void onCopperPowerChange(BlockState state, Level world, BlockPos pos,
            Block sourceBlock, @Nullable Orientation wireOrientation, boolean notify, CallbackInfo ci) {
        if (world.isClientSide()) {
            return;
        }

        Set<BlockPos> updating = IS_UPDATING.get();
        if (updating.contains(pos)) {
            return;
        }

        // Check if this bulb has adjacent copper (determines if we take over)
        boolean hasAdjacentCopper = false;
        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = pos.relative(direction);
            if (ConductiveCopper.isConductiveCopper(world.getBlockState(adjacentPos))) {
                hasAdjacentCopper = true;
                break;
            }
        }

        if (!hasAdjacentCopper) {
            return;
        }

        // Get CURRENT state from world (parameter might be stale)
        BlockState currentState = world.getBlockState(pos);
        boolean currentlyPowered = currentState.getValue(BlockStateProperties.POWERED);
        boolean currentlyLit = currentState.getValue(BlockStateProperties.LIT);

        // With the emission mixin active, vanilla's power query includes copper-conducted power
        boolean shouldBePowered = world.hasNeighborSignal(pos);

        try {
            updating.add(pos);

            if (shouldBePowered && !currentlyPowered) {
                // Rising edge - toggle LIT and set POWERED
                boolean newLit = !currentlyLit;
                BlockState newState = currentState.setValue(BlockStateProperties.POWERED, true)
                                                  .setValue(BlockStateProperties.LIT, newLit);
                world.setBlock(pos, newState, Block.UPDATE_ALL);
                world.playSound(null, pos, newLit ?
                    net.minecraft.sounds.SoundEvents.COPPER_BULB_TURN_ON :
                    net.minecraft.sounds.SoundEvents.COPPER_BULB_TURN_OFF,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            } else if (!shouldBePowered && currentlyPowered) {
                // Falling edge - just clear POWERED
                world.setBlock(pos, currentState.setValue(BlockStateProperties.POWERED, false), Block.UPDATE_ALL);
            }

            ci.cancel();
        } finally {
            updating.remove(pos);
        }
    }
}
