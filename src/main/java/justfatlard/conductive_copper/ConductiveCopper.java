package justfatlard.conductive_copper;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

public class ConductiveCopper implements ModInitializer {
    public static final String MOD_ID = "conductive_copper";
    public static final int MAX_NETWORK_SIZE = 256;
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Maps blocks to their oxidation level (resistance per block).
    // Unoxidized = 0, Exposed = 1, Weathered = 2, Oxidized = 3
    private static final Map<Block, Integer> OXIDATION_RESISTANCE = new HashMap<>();
    static {
        // Unoxidized (0 resistance)
        for (Block b : new Block[]{
            Blocks.COPPER_BLOCK, Blocks.WAXED_COPPER_BLOCK,
            Blocks.CUT_COPPER, Blocks.WAXED_CUT_COPPER,
            Blocks.CHISELED_COPPER, Blocks.WAXED_CHISELED_COPPER,
            Blocks.COPPER_GRATE, Blocks.WAXED_COPPER_GRATE,
            Blocks.CUT_COPPER_STAIRS, Blocks.WAXED_CUT_COPPER_STAIRS,
            Blocks.CUT_COPPER_SLAB, Blocks.WAXED_CUT_COPPER_SLAB,
            Blocks.COPPER_BULB, Blocks.WAXED_COPPER_BULB
        }) { OXIDATION_RESISTANCE.put(b, 0); }

        // Exposed (1 resistance)
        for (Block b : new Block[]{
            Blocks.EXPOSED_COPPER, Blocks.WAXED_EXPOSED_COPPER,
            Blocks.EXPOSED_CUT_COPPER, Blocks.WAXED_EXPOSED_CUT_COPPER,
            Blocks.EXPOSED_CHISELED_COPPER, Blocks.WAXED_EXPOSED_CHISELED_COPPER,
            Blocks.EXPOSED_COPPER_GRATE, Blocks.WAXED_EXPOSED_COPPER_GRATE,
            Blocks.EXPOSED_CUT_COPPER_STAIRS, Blocks.WAXED_EXPOSED_CUT_COPPER_STAIRS,
            Blocks.EXPOSED_CUT_COPPER_SLAB, Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB,
            Blocks.EXPOSED_COPPER_BULB, Blocks.WAXED_EXPOSED_COPPER_BULB
        }) { OXIDATION_RESISTANCE.put(b, 1); }

        // Weathered (2 resistance)
        for (Block b : new Block[]{
            Blocks.WEATHERED_COPPER, Blocks.WAXED_WEATHERED_COPPER,
            Blocks.WEATHERED_CUT_COPPER, Blocks.WAXED_WEATHERED_CUT_COPPER,
            Blocks.WEATHERED_CHISELED_COPPER, Blocks.WAXED_WEATHERED_CHISELED_COPPER,
            Blocks.WEATHERED_COPPER_GRATE, Blocks.WAXED_WEATHERED_COPPER_GRATE,
            Blocks.WEATHERED_CUT_COPPER_STAIRS, Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS,
            Blocks.WEATHERED_CUT_COPPER_SLAB, Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB,
            Blocks.WEATHERED_COPPER_BULB, Blocks.WAXED_WEATHERED_COPPER_BULB
        }) { OXIDATION_RESISTANCE.put(b, 2); }

        // Oxidized (3 resistance)
        for (Block b : new Block[]{
            Blocks.OXIDIZED_COPPER, Blocks.WAXED_OXIDIZED_COPPER,
            Blocks.OXIDIZED_CUT_COPPER, Blocks.WAXED_OXIDIZED_CUT_COPPER,
            Blocks.OXIDIZED_CHISELED_COPPER, Blocks.WAXED_OXIDIZED_CHISELED_COPPER,
            Blocks.OXIDIZED_COPPER_GRATE, Blocks.WAXED_OXIDIZED_COPPER_GRATE,
            Blocks.OXIDIZED_CUT_COPPER_STAIRS, Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS,
            Blocks.OXIDIZED_CUT_COPPER_SLAB, Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB,
            Blocks.OXIDIZED_COPPER_BULB, Blocks.WAXED_OXIDIZED_COPPER_BULB
        }) { OXIDATION_RESISTANCE.put(b, 3); }
    }

    // Derived from OXIDATION_RESISTANCE — single source of truth
    private static final Set<Block> CONDUCTIVE_COPPER_BLOCKS = OXIDATION_RESISTANCE.keySet();

    private static final Set<Block> COPPER_BULBS = Set.of(
        Blocks.COPPER_BULB, Blocks.EXPOSED_COPPER_BULB,
        Blocks.WEATHERED_COPPER_BULB, Blocks.OXIDIZED_COPPER_BULB,
        Blocks.WAXED_COPPER_BULB, Blocks.WAXED_EXPOSED_COPPER_BULB,
        Blocks.WAXED_WEATHERED_COPPER_BULB, Blocks.WAXED_OXIDIZED_COPPER_BULB
    );

    // Recursion guards — centralized here so the full defense system is visible in one place.
    // IS_PROPAGATING: prevents re-entrant copper network propagation (used by CopperBlockMixin)
    // IS_CHECKING_COPPER_POWER: prevents recursive getWeakRedstonePower calls (used by CopperPowerEmissionMixin)
    // CopperBulbMixin maintains its own per-position IS_UPDATING guard (different pattern).
    public static final ThreadLocal<Boolean> IS_PROPAGATING = ThreadLocal.withInitial(() -> false);
    public static final ThreadLocal<Boolean> IS_CHECKING_COPPER_POWER = ThreadLocal.withInitial(() -> false);

    // Signal cache: BlockPos -> int[6] (one slot per Direction ordinal), -1 = uncached.
    // Valid only during a propagation cycle — cleared on both entry and exit.
    private static final ThreadLocal<Map<BlockPos, int[]>> SIGNAL_CACHE =
        ThreadLocal.withInitial(HashMap::new);

    /**
     * Get the resistance (signal loss) for a copper block based on oxidation level.
     * Unknown blocks return Integer.MAX_VALUE (non-conductor).
     */
    public static int getResistance(Block block) {
        return OXIDATION_RESISTANCE.getOrDefault(block, Integer.MAX_VALUE);
    }

    public static int getResistance(BlockState state) {
        return getResistance(state.getBlock());
    }

    public static boolean isConductiveCopper(Block block) {
        return CONDUCTIVE_COPPER_BLOCKS.contains(block);
    }

    public static boolean isConductiveCopper(BlockState state) {
        return isConductiveCopper(state.getBlock());
    }

    public static boolean isCopperBulb(Block block) {
        return COPPER_BULBS.contains(block);
    }

    public static void clearSignalCache() {
        SIGNAL_CACHE.get().clear();
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Conductive Copper loaded!");
    }

    private static class CopperNode implements Comparable<CopperNode> {
        final BlockPos pos;
        final int resistance;

        CopperNode(BlockPos pos, int resistance) {
            this.pos = pos;
            this.resistance = resistance;
        }

        @Override
        public int compareTo(CopperNode other) {
            return Integer.compare(this.resistance, other.resistance);
        }
    }

    /**
     * Trace through connected copper blocks to find the signal strength
     * that should be received from a copper network.
     *
     * Uses Dijkstra's algorithm to find minimum-resistance paths through the copper network.
     * Resistance is based on oxidation level: Unoxidized=0, Exposed=1, Weathered=2, Oxidized=3
     * Final signal = source_power - accumulated_resistance
     */
    public static int getSignalThroughCopper(World world, BlockPos copperPos, Direction fromDirection) {
        // Check cache first
        Map<BlockPos, int[]> cache = SIGNAL_CACHE.get();
        int[] cached = cache.get(copperPos);
        if (cached != null && cached[fromDirection.ordinal()] >= 0) {
            return cached[fromDirection.ordinal()];
        }

        Map<BlockPos, Integer> minResistance = new HashMap<>();
        PriorityQueue<CopperNode> toVisit = new PriorityQueue<>();
        int maxSignal = 0;

        BlockState startState = world.getBlockState(copperPos);
        int startResistance = getResistance(startState);
        toVisit.add(new CopperNode(copperPos, startResistance));
        minResistance.put(copperPos, startResistance);

        while (!toVisit.isEmpty()) {
            CopperNode node = toVisit.poll();
            BlockPos current = node.pos;
            int currentResistance = node.resistance;

            if (currentResistance > minResistance.getOrDefault(current, Integer.MAX_VALUE)) {
                continue;
            }

            // Dead path: no source produces > 15, so this path can't yield positive signal
            if (currentResistance > 15) {
                continue;
            }

            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = current.offset(dir);
                BlockState neighborState = world.getBlockState(neighborPos);

                if (isConductiveCopper(neighborState)) {
                    if (minResistance.size() >= MAX_NETWORK_SIZE) {
                        LOGGER.warn("Copper network at {} exceeded {} blocks, signal may be incomplete", copperPos, MAX_NETWORK_SIZE);
                        continue;
                    }

                    int neighborResistance = currentResistance + getResistance(neighborState);

                    if (neighborResistance < minResistance.getOrDefault(neighborPos, Integer.MAX_VALUE)) {
                        minResistance.put(neighborPos, neighborResistance);
                        toVisit.add(new CopperNode(neighborPos, neighborResistance));
                    }
                } else {
                    // Skip the direction we entered from to prevent the querying block
                    // from being read as its own power source. The deeper protection
                    // against copper-wire-copper feedback loops lives in
                    // traceWireNetworkPower, which skips copper blocks entirely.
                    if (current.equals(copperPos) && dir == fromDirection) {
                        continue;
                    }

                    int power = 0;

                    if (neighborState.getBlock() == Blocks.REDSTONE_WIRE) {
                        power = traceWireNetworkPower(world, neighborPos);
                    } else {
                        Direction queryDir = dir.getOpposite();
                        int weakPower = neighborState.getWeakRedstonePower(world, neighborPos, queryDir);
                        int strongPower = neighborState.getStrongRedstonePower(world, neighborPos, queryDir);
                        power = Math.max(weakPower, strongPower);
                    }

                    if (power > 0) {
                        int effectivePower = Math.max(0, power - currentResistance);
                        maxSignal = Math.max(maxSignal, effectivePower);

                        if (maxSignal >= 15) {
                            storeInCache(cache, copperPos, fromDirection, maxSignal);
                            return maxSignal;
                        }
                    }
                }
            }
        }

        storeInCache(cache, copperPos, fromDirection, maxSignal);
        return maxSignal;
    }

    private static void storeInCache(Map<BlockPos, int[]> cache, BlockPos pos, Direction dir, int value) {
        int[] slots = cache.computeIfAbsent(pos, k -> new int[]{-1, -1, -1, -1, -1, -1});
        slots[dir.ordinal()] = value;
    }

    /**
     * Trace through a wire network to find original power sources (levers, repeaters, etc.)
     * This avoids using copper-boosted power values by following wires back to their source.
     */
    private static int traceWireNetworkPower(World world, BlockPos wirePos) {
        Set<BlockPos> visitedWires = new HashSet<>();
        Queue<BlockPos> wiresToCheck = new ArrayDeque<>();
        int maxPower = 0;

        wiresToCheck.add(wirePos);
        visitedWires.add(wirePos);

        while (!wiresToCheck.isEmpty()) {
            if (visitedWires.size() >= MAX_NETWORK_SIZE) {
                LOGGER.warn("Wire network at {} exceeded {} blocks, signal may be incomplete", wirePos, MAX_NETWORK_SIZE);
                break;
            }

            BlockPos currentWire = wiresToCheck.poll();

            for (Direction dir : Direction.values()) {
                BlockPos adjacentPos = currentWire.offset(dir);

                if (visitedWires.contains(adjacentPos)) {
                    continue;
                }

                BlockState adjacentState = world.getBlockState(adjacentPos);

                if (isConductiveCopper(adjacentState)) {
                    continue;
                }

                if (adjacentState.getBlock() == Blocks.REDSTONE_WIRE) {
                    visitedWires.add(adjacentPos);
                    wiresToCheck.add(adjacentPos);
                } else {
                    int srcPower = adjacentState.getWeakRedstonePower(world, adjacentPos, dir.getOpposite());
                    srcPower = Math.max(srcPower, adjacentState.getStrongRedstonePower(world, adjacentPos, dir.getOpposite()));
                    maxPower = Math.max(maxPower, srcPower);

                    if (maxPower >= 15) {
                        return maxPower;
                    }
                }
            }
        }

        return maxPower;
    }
}
