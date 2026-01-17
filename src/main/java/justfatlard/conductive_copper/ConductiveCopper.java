package justfatlard.conductive_copper;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

public class ConductiveCopper implements ModInitializer {
    public static final String MOD_ID = "conductive_copper";

    // All copper blocks that can conduct redstone (unwaxed and waxed)
    private static final Set<Block> CONDUCTIVE_COPPER_BLOCKS = Set.of(
        // Full blocks - unwaxed
        Blocks.COPPER_BLOCK,
        Blocks.EXPOSED_COPPER,
        Blocks.WEATHERED_COPPER,
        Blocks.OXIDIZED_COPPER,
        // Full blocks - waxed
        Blocks.WAXED_COPPER_BLOCK,
        Blocks.WAXED_EXPOSED_COPPER,
        Blocks.WAXED_WEATHERED_COPPER,
        Blocks.WAXED_OXIDIZED_COPPER,
        // Cut variants - unwaxed
        Blocks.CUT_COPPER,
        Blocks.EXPOSED_CUT_COPPER,
        Blocks.WEATHERED_CUT_COPPER,
        Blocks.OXIDIZED_CUT_COPPER,
        // Cut variants - waxed
        Blocks.WAXED_CUT_COPPER,
        Blocks.WAXED_EXPOSED_CUT_COPPER,
        Blocks.WAXED_WEATHERED_CUT_COPPER,
        Blocks.WAXED_OXIDIZED_CUT_COPPER,
        // Chiseled variants - unwaxed
        Blocks.CHISELED_COPPER,
        Blocks.EXPOSED_CHISELED_COPPER,
        Blocks.WEATHERED_CHISELED_COPPER,
        Blocks.OXIDIZED_CHISELED_COPPER,
        // Chiseled variants - waxed
        Blocks.WAXED_CHISELED_COPPER,
        Blocks.WAXED_EXPOSED_CHISELED_COPPER,
        Blocks.WAXED_WEATHERED_CHISELED_COPPER,
        Blocks.WAXED_OXIDIZED_CHISELED_COPPER,
        // Grates - unwaxed
        Blocks.COPPER_GRATE,
        Blocks.EXPOSED_COPPER_GRATE,
        Blocks.WEATHERED_COPPER_GRATE,
        Blocks.OXIDIZED_COPPER_GRATE,
        // Grates - waxed
        Blocks.WAXED_COPPER_GRATE,
        Blocks.WAXED_EXPOSED_COPPER_GRATE,
        Blocks.WAXED_WEATHERED_COPPER_GRATE,
        Blocks.WAXED_OXIDIZED_COPPER_GRATE,
        // Stairs - unwaxed
        Blocks.CUT_COPPER_STAIRS,
        Blocks.EXPOSED_CUT_COPPER_STAIRS,
        Blocks.WEATHERED_CUT_COPPER_STAIRS,
        Blocks.OXIDIZED_CUT_COPPER_STAIRS,
        // Stairs - waxed
        Blocks.WAXED_CUT_COPPER_STAIRS,
        Blocks.WAXED_EXPOSED_CUT_COPPER_STAIRS,
        Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS,
        Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS,
        // Slabs - unwaxed
        Blocks.CUT_COPPER_SLAB,
        Blocks.EXPOSED_CUT_COPPER_SLAB,
        Blocks.WEATHERED_CUT_COPPER_SLAB,
        Blocks.OXIDIZED_CUT_COPPER_SLAB,
        // Slabs - waxed
        Blocks.WAXED_CUT_COPPER_SLAB,
        Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB,
        Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB,
        Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB,
        // Bulbs (copper lamps) - unwaxed
        Blocks.COPPER_BULB,
        Blocks.EXPOSED_COPPER_BULB,
        Blocks.WEATHERED_COPPER_BULB,
        Blocks.OXIDIZED_COPPER_BULB,
        // Bulbs (copper lamps) - waxed
        Blocks.WAXED_COPPER_BULB,
        Blocks.WAXED_EXPOSED_COPPER_BULB,
        Blocks.WAXED_WEATHERED_COPPER_BULB,
        Blocks.WAXED_OXIDIZED_COPPER_BULB
    );

    // Maps blocks to their oxidation level (resistance per block)
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

    /**
     * Get the resistance (signal loss) for a copper block based on oxidation level.
     * Unoxidized = 0, Exposed = 1, Weathered = 2, Oxidized = 3
     */
    public static int getResistance(Block block) {
        return OXIDATION_RESISTANCE.getOrDefault(block, 0);
    }

    public static int getResistance(BlockState state) {
        return getResistance(state.getBlock());
    }

    @Override
    public void onInitialize() {
        System.out.println("[" + MOD_ID + "] Conductive Copper loaded!");
    }

    /**
     * Check if a block is a conductive copper block (unwaxed or waxed).
     * Both conduct redstone, but oxidation level affects resistance.
     */
    public static boolean isConductiveCopper(Block block) {
        return CONDUCTIVE_COPPER_BLOCKS.contains(block);
    }

    public static boolean isConductiveCopper(BlockState state) {
        return isConductiveCopper(state.getBlock());
    }

    /**
     * Helper class for Dijkstra priority queue - tracks position and accumulated resistance
     */
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

            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = current.offset(dir);
                BlockState neighborState = world.getBlockState(neighborPos);

                if (isConductiveCopper(neighborState)) {
                    int neighborResistance = currentResistance + getResistance(neighborState);

                    if (neighborResistance < minResistance.getOrDefault(neighborPos, Integer.MAX_VALUE)) {
                        minResistance.put(neighborPos, neighborResistance);
                        toVisit.add(new CopperNode(neighborPos, neighborResistance));
                    }
                } else {
                    // Skip the original direction we came from to avoid feedback loops
                    if (current.equals(copperPos) && dir == fromDirection) {
                        continue;
                    }

                    int power = 0;

                    // Special handling for redstone wire: trace through wire network to find
                    // original power sources (levers, repeaters, etc.) - NOT copper-boosted power
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
                    }
                }
            }
        }

        return maxSignal;
    }

    /**
     * Trace through a wire network to find original power sources (levers, repeaters, etc.)
     * This avoids using copper-boosted power values by following wires back to their source.
     */
    private static int traceWireNetworkPower(World world, BlockPos wirePos) {
        Set<BlockPos> visitedWires = new HashSet<>();
        Queue<BlockPos> wiresToCheck = new LinkedList<>();
        int maxPower = 0;

        wiresToCheck.add(wirePos);
        visitedWires.add(wirePos);

        while (!wiresToCheck.isEmpty()) {
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
                }
            }
        }

        return maxPower;
    }

    /**
     * Check if there's a copper block adjacent to the given position
     * that has a redstone signal coming into it.
     */
    public static int getCopperConductedSignal(World world, BlockPos pos, Direction direction) {
        BlockPos adjacentPos = pos.offset(direction);
        BlockState adjacentState = world.getBlockState(adjacentPos);

        if (isConductiveCopper(adjacentState)) {
            return getSignalThroughCopper(world, adjacentPos, direction.getOpposite());
        }

        return 0;
    }

    private static final ThreadLocal<Boolean> IS_PROPAGATING_FROM_WIRE = ThreadLocal.withInitial(() -> false);

    /**
     * Called when a powered wire is adjacent to copper.
     * Propagates updates through the copper network to all other wires.
     */
    public static void propagateFromPoweredWire(World world, BlockPos copperPos, BlockPos sourceWirePos) {
        if (IS_PROPAGATING_FROM_WIRE.get()) {
            return;
        }

        try {
            IS_PROPAGATING_FROM_WIRE.set(true);

            Set<BlockPos> visited = new HashSet<>();
            Set<BlockPos> wiresToUpdate = new HashSet<>();
            Queue<BlockPos> toVisit = new LinkedList<>();

            toVisit.add(copperPos);
            visited.add(copperPos);

            while (!toVisit.isEmpty()) {
                BlockPos current = toVisit.poll();

                for (Direction dir : Direction.values()) {
                    BlockPos neighborPos = current.offset(dir);

                    if (visited.contains(neighborPos)) {
                        continue;
                    }

                    BlockState neighborState = world.getBlockState(neighborPos);

                    if (isConductiveCopper(neighborState)) {
                        visited.add(neighborPos);
                        toVisit.add(neighborPos);
                    } else if (neighborState.getBlock() == Blocks.REDSTONE_WIRE) {
                        if (!neighborPos.equals(sourceWirePos)) {
                            wiresToUpdate.add(neighborPos);
                        }
                    }
                }
            }

            for (BlockPos wirePos : wiresToUpdate) {
                world.updateNeighbor(wirePos, Blocks.COPPER_BLOCK, null);
            }
        } finally {
            IS_PROPAGATING_FROM_WIRE.set(false);
        }
    }
}
