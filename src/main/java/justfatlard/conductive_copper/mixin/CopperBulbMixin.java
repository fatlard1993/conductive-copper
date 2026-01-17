package justfatlard.conductive_copper.mixin;

import justfatlard.conductive_copper.ConductiveCopper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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

/**
 * Mixin to make copper bulbs toggle their light when receiving copper-conducted power.
 * Mimics vanilla toggle behavior: LIT toggles on rising edge (unpowered -> powered).
 */
@Mixin(BulbBlock.class)
public class CopperBulbMixin {

    @Unique
    private static final ThreadLocal<Boolean> IS_UPDATING = ThreadLocal.withInitial(() -> false);

    @Inject(method = "neighborUpdate", at = @At("HEAD"), cancellable = true)
    private void onCopperPowerChange(BlockState state, World world, BlockPos pos,
            Block sourceBlock, @Nullable WireOrientation wireOrientation, boolean notify, CallbackInfo ci) {
        if (world.isClient() || IS_UPDATING.get()) {
            return;
        }

        // Get CURRENT state from world (parameter might be stale)
        BlockState currentState = world.getBlockState(pos);
        boolean currentlyPowered = currentState.get(Properties.POWERED);
        boolean currentlyLit = currentState.get(Properties.LIT);

        // Check if this bulb is part of a copper network (has adjacent copper)
        boolean hasAdjacentCopper = false;
        boolean hasCopperPower = false;

        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = pos.offset(direction);
            BlockState adjacentState = world.getBlockState(adjacentPos);

            if (ConductiveCopper.isConductiveCopper(adjacentState)) {
                hasAdjacentCopper = true;
                int copperPower = ConductiveCopper.getSignalThroughCopper(
                    world,
                    adjacentPos,
                    direction.getOpposite()
                );

                if (copperPower > 0) {
                    hasCopperPower = true;
                    break;
                }
            }
        }

        // Check vanilla power from non-wire sources only
        boolean hasVanillaPower = false;
        for (Direction dir : Direction.values()) {
            BlockPos checkPos = pos.offset(dir);
            BlockState checkState = world.getBlockState(checkPos);
            if (checkState.getBlock() == Blocks.REDSTONE_WIRE) {
                continue;
            }
            if (ConductiveCopper.isConductiveCopper(checkState)) {
                continue;
            }
            int power = checkState.getWeakRedstonePower(world, checkPos, dir.getOpposite());
            power = Math.max(power, checkState.getStrongRedstonePower(world, checkPos, dir.getOpposite()));
            if (power > 0) {
                hasVanillaPower = true;
                break;
            }
        }

        // If no adjacent copper, let vanilla handle this bulb completely
        if (!hasAdjacentCopper) {
            return;
        }

        // This bulb is part of a copper network - we handle ALL power logic
        boolean shouldBePowered = hasVanillaPower || hasCopperPower;

        try {
            IS_UPDATING.set(true);

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

            // Always cancel vanilla for bulbs in copper networks - we handle everything
            ci.cancel();
        } finally {
            IS_UPDATING.set(false);
        }
    }
}
