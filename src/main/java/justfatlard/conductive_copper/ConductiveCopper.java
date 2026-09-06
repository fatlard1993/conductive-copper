package justfatlard.conductive_copper;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import justfatlard.conductive_copper.integration.MixedSlabIntegration;
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

    // What a signal loses crossing each conductor. Copper pays for its oxidation; gold does not
    // oxidise and pays nothing.
    //
    // Weathering copper variants are not individual Blocks.* constants: they hang off
    // WeatheringCopperCollection<Block> via .weathering()/.waxed(), then
    // .unaffected()/.exposed()/.weathered()/.oxidized() on the resulting ByState<Block>.
    private static final Map<Block, Integer> RESISTANCE = new HashMap<>();
    private static final Set<Block> COPPER_BULBS = new HashSet<>();

    static {
        addFamily(Blocks.COPPER_BLOCK, false);
        addFamily(Blocks.CUT_COPPER, false);
        addFamily(Blocks.CHISELED_COPPER, false);
        addFamily(Blocks.COPPER_GRATE, false);
        addFamily(Blocks.CUT_COPPER_STAIRS, false);
        addFamily(Blocks.CUT_COPPER_SLAB, false);
        addFamily(Blocks.COPPER_BULB, true);

        RESISTANCE.put(Blocks.GOLD_BLOCK, 0);
    }

    /** Registers all eight weathering/waxed variants of a copper block family. */
    private static void addFamily(WeatheringCopperCollection<Block> family, boolean isBulb) {
        WeatheringCopperCollection.ByState<Block> unwaxed = family.weathering();
        WeatheringCopperCollection.ByState<Block> waxed = family.waxed();

        RESISTANCE.put(unwaxed.unaffected(), 0);
        RESISTANCE.put(waxed.unaffected(), 0);
        RESISTANCE.put(unwaxed.exposed(), 1);
        RESISTANCE.put(waxed.exposed(), 1);
        RESISTANCE.put(unwaxed.weathered(), 2);
        RESISTANCE.put(waxed.weathered(), 2);
        RESISTANCE.put(unwaxed.oxidized(), 3);
        RESISTANCE.put(waxed.oxidized(), 3);

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

    private static final Set<Block> CONDUCTORS = RESISTANCE.keySet();

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
        return RESISTANCE.getOrDefault(block, Integer.MAX_VALUE);
    }

    /**
     * What a signal loses crossing this block.
     *
     * <p>Sees through a mixed slab, and <b>must</b>, because this and {@link #isConductor}
     * are read as a pair: the walk asks whether a neighbour conducts and then immediately asks
     * what crossing it costs. Teaching only the first one left copper inside a mixed slab
     * conducting at a cost of {@code Integer.MAX_VALUE}, which the walk added to a running total
     * and overflowed into a negative - a path that got cheaper the further it went.
     *
     * <p><b>The cheapest half wins.</b> With one copper half the other is simply not in the
     * signal's way, so the copper's own oxidation is the whole answer. With two - a mixed slab can
     * hold any pair of the eight cut copper variants - they are two conductors sharing one block,
     * which is a parallel circuit: the current goes the better way, and an oxidised half beside a
     * clean one costs what the clean one costs. Taking the worse of the two, or averaging them,
     * would mean a block conducted worse for having more metal in it.
     */
    public static int getResistance(BlockState state) {
        int direct = getResistance(state.getBlock());
        if (direct != Integer.MAX_VALUE) return direct;

        int best = Integer.MAX_VALUE;
        for (Block half : MixedSlabIntegration.halvesOf(state)) {
            best = Math.min(best, getResistance(half));
        }
        return best;
    }

    /** The same question block-tip asks, seeing through a mixed slab as everything else does. */
    public static Integer resistanceOf(BlockState state) {
        int resistance = getResistance(state);
        return resistance == Integer.MAX_VALUE ? null : resistance;
    }

    public static boolean isConductor(Block block) {
        return CONDUCTORS.contains(block);
    }

    public static boolean isConductor(BlockState state) {
        if (isConductor(state.getBlock())) return true;

        // A cut copper slab built into a mixed slab is still copper and still touching its
        // neighbours; only its block identity changed. One copper half is enough - the metal is
        // there, and asking for both would make a copper-and-oak step a wall the signal dies at.
        for (Block half : MixedSlabIntegration.halvesOf(state)) {
            if (isConductor(half)) return true;
        }
        return false;
    }

    public static boolean isCopperBulb(Block block) {
        return COPPER_BULBS.contains(block);
    }

    public static void clearSignalCache() {
        SIGNAL_CACHE.get().clear();
    }

    @Override
    public void onInitialize() {
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("block-tip")) {
            justfatlard.conductive_copper.integration.CopperTipRegistration.register();
        }

		// Guarded class load: SignalQuestRegistration names village-quests types
		// directly, so it must not be touched when that mod is absent.
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("village-quests-justfatlard")) {
			justfatlard.conductive_copper.integration.SignalQuestRegistration.register();
		}

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

        int signal = walk(world, copperPos, fromDirection);
        storeInCache(cache, copperPos, fromDirection, signal);
        return signal;
    }

    /**
     * What the network is carrying at this block, asked by nobody in particular.
     *
     * <p>The redstone path always asks on behalf of a neighbour, and leaves that neighbour out so
     * it cannot be read as its own source. A player looking at the block has no such seat, so
     * every side counts. Runs outside a propagation cycle, so it stays out of the signal cache,
     * which is only valid inside one, and raises the same re-entry guard the emission mixin does.
     */
    public static int signalAt(Level world, BlockPos pos) {
        if (IS_CHECKING_COPPER_POWER.get()) return 0;

        try {
            IS_CHECKING_COPPER_POWER.set(true);
            return walk(world, pos, null);
        } finally {
            IS_CHECKING_COPPER_POWER.set(false);
        }
    }

    /** @param fromDirection the querying neighbour's side, skipped at the start block; null skips nothing */
    private static int walk(Level world, BlockPos copperPos, Direction fromDirection) {
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

                if (isConductor(neighborState)) {
                    if (minResistance.size() >= MAX_NETWORK_SIZE) {
                        LOGGER.warn("Copper network at {} exceeded {} blocks, signal may be incomplete", copperPos, MAX_NETWORK_SIZE);
                        continue;
                    }

                    int crossing = getResistance(neighborState);
                    // Belt and braces against the pair disagreeing again: anything that says it
                    // conducts but cannot price the crossing is dropped rather than added to a
                    // running total, where MAX_VALUE would wrap the sum negative and make the
                    // path look free.
                    if (crossing == Integer.MAX_VALUE) continue;

                    int neighborResistance = currentResistance + crossing;

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
                            return maxSignal;
                        }
                    }
                }
            }
        }

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

                if (isConductor(adjacentState)) {
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
