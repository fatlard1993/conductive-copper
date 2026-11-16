package justfatlard.conductive_copper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Copper networks with no redstone source touching them, whose neighbours have already been told
 * so.
 *
 * <p>A network like that carries no signal, and once its neighbours have heard as much, telling
 * them again changes nothing. So a door swinging or water flowing beside decorative copper - a
 * trial chamber's walls - does not walk the network and wake everything along it on every update.
 *
 * <p>The answer holds until the network or what powers it could have changed: a block becoming or
 * ceasing to be a conductor anywhere in the world, or a redstone source appearing anywhere in it.
 * Either forgets every quiet network in that world, since telling which one it touched would take
 * the walk this exists to save. A source next to the updated block is checked as well, for one
 * that arrived some way the world did not announce.
 */
public final class QuietNetworks {
    private QuietNetworks() {}

    /** Past this many remembered blocks in one world, it starts over rather than grow without end. */
    private static final int MAX_REMEMBERED = 1 << 16;

    private static final Map<Level, Set<BlockPos>> QUIET = new WeakHashMap<>();
    private static volatile boolean any = false;

    public static boolean isSource(BlockState state) {
        return state.is(Blocks.REDSTONE_WIRE) || state.isSignalSource();
    }

    /** Whether an update at this conductor can be let pass: its network is quiet and nothing beside it powers it. */
    public static boolean stillQuiet(Level world, BlockPos pos) {
        if (!any) return false;

        synchronized (QUIET) {
            Set<BlockPos> quiet = QUIET.get(world);
            if (quiet == null || !quiet.contains(pos)) return false;
        }

        for (Direction dir : Direction.values()) {
            BlockState neighbour = world.getBlockState(pos.relative(dir));
            if (!ConductiveCopper.isConductor(neighbour) && isSource(neighbour)) return false;
        }
        return true;
    }

    /** A whole network was walked and its neighbours told: remember it quiet, or forget it if it is not. */
    public static void walked(Level world, Set<BlockPos> network, boolean quiet) {
        synchronized (QUIET) {
            if (quiet) {
                Set<BlockPos> known = QUIET.computeIfAbsent(world, w -> new HashSet<>());
                if (known.size() + network.size() > MAX_REMEMBERED) known.clear();
                known.addAll(network);
                any = true;
            } else {
                Set<BlockPos> known = QUIET.get(world);
                if (known != null) known.removeAll(network);
            }
        }
    }

    /** A block in this world changed from {@code old} to {@code state}. */
    public static void changed(Level world, BlockState state, BlockState old) {
        if (!any) return;

        boolean reshaped = ConductiveCopper.isConductor(state) != ConductiveCopper.isConductor(old);
        if (reshaped || (isSource(state) && !isSource(old))) forget(world);
    }

    public static void forget(Level world) {
        if (!any) return;

        synchronized (QUIET) {
            Set<BlockPos> known = QUIET.get(world);
            if (known != null) known.clear();
        }
    }
}
