package justfatlard.conductive_copper.mixin;

import justfatlard.conductive_copper.ConductiveCopper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BulbBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
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
@Mixin(BulbBlock.class)
public class CopperBulbMixin {

    // Per-position guard so Bulb A toggling doesn't suppress Bulb B's update
    @Unique
    private static final ThreadLocal<Set<BlockPos>> IS_UPDATING = ThreadLocal.withInitial(HashSet::new);

    @Inject(method = "neighborUpdate", at = @At("HEAD"), cancellable = true)
    private void onCopperPowerChange(BlockState state, World world, BlockPos pos,
            Block sourceBlock, @Nullable WireOrientation wireOrientation, boolean notify, CallbackInfo ci) {
        if (world.isClient()) {
            return;
        }

        Set<BlockPos> updating = IS_UPDATING.get();
        if (updating.contains(pos)) {
            return;
        }

        // Check if this bulb has adjacent copper (determines if we take over)
        boolean hasAdjacentCopper = false;
        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = pos.offset(direction);
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
        boolean currentlyPowered = currentState.get(Properties.POWERED);
        boolean currentlyLit = currentState.get(Properties.LIT);

        // With the emission mixin active, vanilla's power query includes copper-conducted power
        boolean shouldBePowered = world.isReceivingRedstonePower(pos);

        try {
            updating.add(pos);

            if (shouldBePowered && !currentlyPowered) {
                // Rising edge - toggle LIT and set POWERED
                boolean newLit = !currentlyLit;
                BlockState newState = currentState.with(Properties.POWERED, true)
                                                  .with(Properties.LIT, newLit);
                world.setBlockState(pos, newState, Block.NOTIFY_ALL);
                world.playSound(null, pos, newLit ?
                    net.minecraft.sound.SoundEvents.BLOCK_COPPER_BULB_TURN_ON :
                    net.minecraft.sound.SoundEvents.BLOCK_COPPER_BULB_TURN_OFF,
                    net.minecraft.sound.SoundCategory.BLOCKS, 1.0F, 1.0F);
            } else if (!shouldBePowered && currentlyPowered) {
                // Falling edge - just clear POWERED
                world.setBlockState(pos, currentState.with(Properties.POWERED, false), Block.NOTIFY_ALL);
            }

            ci.cancel();
        } finally {
            updating.remove(pos);
        }
    }
}
