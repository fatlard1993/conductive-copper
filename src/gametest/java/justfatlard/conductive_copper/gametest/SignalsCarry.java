package justfatlard.conductive_copper.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Copper carrying a signal the way a player builds it: a lever lighting a lamp down a copper line,
 * oxidation costing strength, gold costing none, a copper bulb toggling, and a lamp that hears
 * about a source that appears, disappears, or is joined to it by a new block of copper.
 *
 * <p>Full level only. Each lane is built in the sky over the spawn, so nothing the world generated
 * stands in the way.
 */
public final class SignalsCarry implements FabricClientGameTest {

	private static final int SETTLE = 8;
	private static final Block COPPER = Blocks.COPPER_BLOCK.weathering().unaffected();
	private static final Block EXPOSED_COPPER = Blocks.COPPER_BLOCK.weathering().exposed();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!"full".equals(System.getProperty("pandorical.test.level", "smoke"))) return;

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			BlockPos spawn = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			BlockPos origin = new BlockPos(spawn.getX() - 12, 200, spawn.getZ() - 12);

			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				for (int x = -1; x <= 26; x++) for (int z = -1; z <= 20; z++) {
					level.setBlockAndUpdate(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState());
					for (int y = 0; y <= 2; y++) level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
				}
			});
			context.waitTicks(SETTLE);

			// A lever down five blocks of copper lights a lamp, and turning it off puts it out.
			BlockPos a = origin;
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				level.setBlockAndUpdate(a, lever(false));
				line(level, a.east(), 5, COPPER);
				level.setBlockAndUpdate(a.east(6), Blocks.REDSTONE_LAMP.defaultBlockState());
			});
			context.waitTicks(SETTLE);
			server.runOnServer(s -> toggle(s.overworld(), a));
			context.waitTicks(SETTLE);
			check(lit(server, a.east(6)), "a lever down a copper line did not light the lamp");
			server.runOnServer(s -> toggle(s.overworld(), a));
			context.waitTicks(SETTLE);
			check(!lit(server, a.east(6)), "turning the lever off left the lamp lit");

			// Three blocks of exposed copper cost one each: wire at the end reads twelve.
			BlockPos b = origin.south(2);
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				line(level, b.east(), 3, EXPOSED_COPPER);
				level.setBlockAndUpdate(b.east(4), Blocks.REDSTONE_WIRE.defaultBlockState());
				level.setBlockAndUpdate(b, Blocks.REDSTONE_BLOCK.defaultBlockState());
			});
			context.waitTicks(SETTLE);
			int exposed = power(server, b.east(4));
			check(exposed == 12, "wire after three exposed copper read " + exposed + ", not 12");
			server.runOnServer(s -> s.overworld().setBlockAndUpdate(b, Blocks.AIR.defaultBlockState()));
			context.waitTicks(SETTLE);
			int gone = power(server, b.east(4));
			check(gone == 0, "wire still read " + gone + " after the source was mined");

			// Gold costs nothing, however far.
			BlockPos g = origin.south(4);
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				line(level, g.east(), 20, Blocks.GOLD_BLOCK);
				level.setBlockAndUpdate(g.east(21), Blocks.REDSTONE_WIRE.defaultBlockState());
				level.setBlockAndUpdate(g, Blocks.REDSTONE_BLOCK.defaultBlockState());
			});
			context.waitTicks(SETTLE);
			int golden = power(server, g.east(21));
			check(golden == 15, "wire after twenty gold read " + golden + ", not 15");

			// A network with nothing powering it, nudged by a block that is no signal, then given a
			// source, losing it, and getting it back: the lamp follows every time.
			BlockPos c = origin.south(6);
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				line(level, c.east(), 5, COPPER);
				level.setBlockAndUpdate(c.east(6), Blocks.REDSTONE_LAMP.defaultBlockState());
			});
			context.waitTicks(SETTLE);
			server.runOnServer(s -> s.overworld().setBlockAndUpdate(c.east(3).above(), Blocks.GLASS.defaultBlockState()));
			context.waitTicks(SETTLE);
			check(!lit(server, c.east(6)), "an unpowered copper line lit its lamp");
			for (int round = 0; round < 2; round++) {
				server.runOnServer(s -> s.overworld().setBlockAndUpdate(c, Blocks.REDSTONE_BLOCK.defaultBlockState()));
				context.waitTicks(SETTLE);
				check(lit(server, c.east(6)), "a source placed against a quiet copper line did not reach the lamp");
				server.runOnServer(s -> s.overworld().setBlockAndUpdate(c.east(3).above(), Blocks.AIR.defaultBlockState()));
				context.waitTicks(SETTLE);
				check(lit(server, c.east(6)), "a block beside a powered line put its lamp out");
				server.runOnServer(s -> s.overworld().setBlockAndUpdate(c, Blocks.AIR.defaultBlockState()));
				context.waitTicks(SETTLE);
				check(!lit(server, c.east(6)), "mining the source left the lamp lit");
				server.runOnServer(s -> s.overworld().setBlockAndUpdate(c.east(3).above(), Blocks.GLASS.defaultBlockState()));
				context.waitTicks(SETTLE);
				check(!lit(server, c.east(6)), "a block beside a quiet line lit its lamp");
			}

			// A powered line joined to a quiet one by a new block of copper: the next update on the
			// quiet side carries the signal through to its lamp.
			BlockPos d = origin.south(8);
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				line(level, d.east(), 2, COPPER);
				line(level, d.east(4), 3, COPPER);
				level.setBlockAndUpdate(d.east(7), Blocks.REDSTONE_LAMP.defaultBlockState());
				level.setBlockAndUpdate(d, Blocks.REDSTONE_BLOCK.defaultBlockState());
			});
			context.waitTicks(SETTLE);
			server.runOnServer(s -> s.overworld().setBlockAndUpdate(d.east(5).above(), Blocks.GLASS.defaultBlockState()));
			context.waitTicks(SETTLE);
			check(!lit(server, d.east(7)), "a line not yet joined to the source lit its lamp");
			server.runOnServer(s -> s.overworld().setBlockAndUpdate(d.east(3), COPPER.defaultBlockState()));
			context.waitTicks(SETTLE);
			server.runOnServer(s -> s.overworld().setBlockAndUpdate(d.east(6).above(), Blocks.GLASS.defaultBlockState()));
			context.waitTicks(SETTLE);
			check(lit(server, d.east(7)), "joining the lines did not carry the signal to the lamp");

			// A line cut by mining a block of copper: the next update on the cut-off side puts the lamp out.
			BlockPos r = origin.south(10);
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				line(level, r.east(), 3, COPPER);
				level.setBlockAndUpdate(r.east(4), Blocks.REDSTONE_LAMP.defaultBlockState());
				level.setBlockAndUpdate(r, Blocks.REDSTONE_BLOCK.defaultBlockState());
			});
			context.waitTicks(SETTLE);
			check(lit(server, r.east(4)), "a redstone block down a copper line did not light the lamp");
			server.runOnServer(s -> s.overworld().setBlockAndUpdate(r.east(2), Blocks.AIR.defaultBlockState()));
			context.waitTicks(SETTLE);
			server.runOnServer(s -> s.overworld().setBlockAndUpdate(r.east(3).above(), Blocks.GLASS.defaultBlockState()));
			context.waitTicks(SETTLE);
			check(!lit(server, r.east(4)), "a lamp cut off from its source stayed lit");

			// Wire into copper: the copper finds the wire's own source and lights the lamp.
			BlockPos w = origin.south(12);
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				line(level, w.east(), 2, Blocks.REDSTONE_WIRE);
				line(level, w.east(3), 2, COPPER);
				level.setBlockAndUpdate(w.east(5), Blocks.REDSTONE_LAMP.defaultBlockState());
				level.setBlockAndUpdate(w, Blocks.REDSTONE_BLOCK.defaultBlockState());
			});
			context.waitTicks(SETTLE);
			check(lit(server, w.east(5)), "wire into copper did not light the lamp");
			server.runOnServer(s -> s.overworld().setBlockAndUpdate(w, Blocks.AIR.defaultBlockState()));
			context.waitTicks(SETTLE);
			check(!lit(server, w.east(5)), "the lamp stayed lit after the wire's source was mined");

			// A copper bulb toggles on each rising edge and ignores the falling one.
			BlockPos e = origin.south(14);
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				level.setBlockAndUpdate(e, lever(false));
				line(level, e.east(), 2, COPPER);
				level.setBlockAndUpdate(e.east(3), Blocks.COPPER_BULB.weathering().unaffected().defaultBlockState());
			});
			context.waitTicks(SETTLE);
			server.runOnServer(s -> toggle(s.overworld(), e));
			context.waitTicks(SETTLE);
			check(lit(server, e.east(3)), "the bulb did not turn on with the lever");
			server.runOnServer(s -> toggle(s.overworld(), e));
			context.waitTicks(SETTLE);
			check(lit(server, e.east(3)), "the bulb turned off when the lever did");
			server.runOnServer(s -> toggle(s.overworld(), e));
			context.waitTicks(SETTLE);
			check(!lit(server, e.east(3)), "the bulb did not turn off on the second pull");
		}
		context.waitTicks(10);
	}

	private static BlockState lever(boolean on) {
		return Blocks.LEVER.defaultBlockState()
			.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
			.setValue(BlockStateProperties.POWERED, on);
	}

	private static void toggle(ServerLevel level, BlockPos pos) {
		level.setBlockAndUpdate(pos, level.getBlockState(pos).cycle(BlockStateProperties.POWERED));
	}

	private static void line(ServerLevel level, BlockPos start, int length, Block block) {
		for (int i = 0; i < length; i++) level.setBlockAndUpdate(start.east(i), block.defaultBlockState());
	}

	private static boolean lit(TestServerContext server, BlockPos pos) {
		return server.computeOnServer(s -> s.overworld().getBlockState(pos).getValue(BlockStateProperties.LIT));
	}

	private static int power(TestServerContext server, BlockPos pos) {
		return server.computeOnServer(s -> s.overworld().getBlockState(pos).getValue(BlockStateProperties.POWER));
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
