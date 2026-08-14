package justfatlard.conductive_copper;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopperCollection;
import net.minecraft.world.level.block.state.BlockState;
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

    // Weathering copper variants are not individual Blocks.* constants: they hang off
    // WeatheringCopperCollection<Block> via .weathering()/.waxed(), then
    // .unaffected()/.exposed()/.weathered()/.oxidized() on the resulting ByState<Block>.
    private static final Map<Block, Integer> OXIDATION_RESISTANCE = new HashMap<>();
    private static final Set<Block> COPPER_BULBS = new HashSet<>();

    static {
        addFamily(Blocks.CUT_COPPER, false);
        addFamily(Blocks.CHISELED_COPPER, false);
        addFamily(Blocks.COPPER_GRATE, false);
        addFamily(Blocks.CUT_COPPER_STAIRS, false);
        addFamily(Blocks.CUT_COPPER_SLAB, false);
        addFamily(Blocks.COPPER_BULB, true);
    }

    /** Registers all eight weathering/waxed variants of a copper block family. */
    private static void addFamily(WeatheringCopperCollection<Block> family, boolean isBulb) {
        WeatheringCopperCollection.ByState<Block> unwaxed = family.weathering();
        WeatheringCopperCollection.ByState<Block> waxed = family.waxed();

        OXIDATION_RESISTANCE.put(unwaxed.unaffected(), 0);
        OXIDATION_RESISTANCE.put(waxed.unaffected(), 0);
        OXIDATION_RESISTANCE.put(unwaxed.exposed(), 1);
        OXIDATION_RESISTANCE.put(waxed.exposed(), 1);
        OXIDATION_RESISTANCE.put(unwaxed.weathered(), 2);
        OXIDATION_RESISTANCE.put(waxed.weathered(), 2);
        OXIDATION_RESISTANCE.put(unwaxed.oxidized(), 3);
        OXIDATION_RESISTANCE.put(waxed.oxidized(), 3);

        if (isBulb) {
            COPPER_BULBS.add(unwaxed.unaffected());
            COPPER_BULBS.add(unwaxed.exposed());
            COPPER_BULBS.add(unwaxed.weathered());
            COPPER_BULBS.add(unwaxed.oxidized());
            COPPER_BULBS.add(waxed.unaffected());
            COPPER_BULBS.add(waxed.exposed());
            COPPER_BULBS.add(waxed.weathered());
            COPPER_BULBS.add(waxed.oxidized());
        }
    }

    private static final Set<Block> CONDUCTIVE_COPPER_BLOCKS = OXIDATION_RESISTANCE.keySet();

    // Recursion guards, centralized here so the full defense system is visible in one place.
    // IS_PROPAGATING: prevents re-entrant copper network propagation (used by CopperBlockMixin)
    // IS_CHECKING_COPPER_POWER: prevents recursive getWeakRedstonePower calls (used by CopperPowerEmissionMixin)
    // CopperBulbMixin maintains its own per-position IS_UPDATING guard (different pattern).
    public static final ThreadLocal<Boolean> IS_PROPAGATING = ThreadLocal.withInitial(() -> false);
    public static final ThreadLocal<Boolean> IS_CHECKING_COPPER_POWER = ThreadLocal.withInitial(() -> false);

    // Signal cache: BlockPos -> int[6] (one slot per Direction ordinal), -1 = uncached.
    // Valid only during a propagation cycle; cleared on both entry and exit.
    private static final ThreadLocal<Map<BlockPos, int[]>> SIGNAL_CACHE =
        ThreadLocal.withInitial(HashMap::new);

    /** Unknown blocks return Integer.MAX_VALUE (non-conductor). */
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
     * Dijkstra over the copper network for minimum-resistance paths.
     * Final signal = source_power - accumulated_resistance.
     */
    public static int getSignalThroughCopper(Level world, BlockPos copperPos, Direction fromDirection) {
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
                BlockPos neighborPos = current.relative(dir);
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
                        int weakPower = neighborState.getSignal(world, neighborPos, queryDir);
                        int strongPower = neighborState.getDirectSignal(world, neighborPos, queryDir);
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
    private static int traceWireNetworkPower(Level world, BlockPos wirePos) {
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
                BlockPos adjacentPos = currentWire.relative(dir);

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
                    int srcPower = adjacentState.getSignal(world, adjacentPos, dir.getOpposite());
                    srcPower = Math.max(srcPower, adjacentState.getDirectSignal(world, adjacentPos, dir.getOpposite()));
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
