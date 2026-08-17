package justfatlard.conductive_copper.quest;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import justfatlard.village_quests.quest.VillagerQuest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A copper signal line that does not reach the end of itself.
 *
 * <p>The mod's whole idea is that oxidation costs signal, and there is no way to
 * find that out by playing: copper looks like copper, the lamp is simply dark,
 * and nothing anywhere says why. So the village hands you one that is broken and
 * you work out that the green blocks are eating it.
 *
 * <p>Built to be solvable without being told the answer. Five oxidized blocks
 * cost exactly the fifteen a lever produces, so the lamp sits at zero: not dim,
 * dark, which reads as "this is broken" rather than "this is nearly working".
 * Scraping one block with an axe moves it to a number you can see change, and
 * that is the moment the mechanic explains itself.
 */
public class SignalLineQuest extends VillagerQuest {
	/** Oxidized costs 3 a block, and a lever makes 15. Five of them is a dead line. */
	private static final int SEGMENTS = 5;

	private BlockPos lampPos;
	private boolean built = false;

	public SignalLineQuest(String requesterName, UUID villagerUuid) {
		super(VillagerQuest.QuestType.CREATION, requesterName, villagerUuid, 6);
	}

	@Override
	public String getDescription() {
		ThreadLocalRandom rng = ThreadLocalRandom.current();
		String[] lines = {
			this.requesterName + ": \"The lamp on the copper run has been dark for a season. The lever still clicks. "
				+ "Nobody can tell me why, and I have stopped asking.\"",
			this.requesterName + ": \"We ran copper to that lamp because someone said it carries a signal. "
				+ "It carried one for a while. Now it does not.\"",
			this.requesterName + ": \"There is a lever, and there is a lamp, and between them there is copper doing nothing. "
				+ "See if you can make it do something.\""
		};
		return lines[rng.nextInt(lines.length)];
	}

	@Override
	public String getObjective() {
		return this.lampPos == null
			? "find the dead copper line near " + this.requesterName
			: "get the lamp lit at " + this.lampPos.getX() + ", " + this.lampPos.getY() + ", " + this.lampPos.getZ();
	}

	/**
	 * Built on acceptance rather than on offer, so a line only appears for someone
	 * who said yes, and only while they are standing there to see it appear.
	 */
	@Override
	public void onAccept(ServerPlayer player) {
		if (this.built) return;
		if (!(player.level() instanceof ServerLevel world)) return;

		BlockPos start = findSpot(world, player.blockPosition());
		if (start == null) return;

		build(world, start);
		this.built = true;
	}

	@Override
	public void onComplete(ServerPlayer player) {
		// The line stays. It works now, it belongs to the village, and taking it
		// away would undo the only evidence that any of this happened.
	}

	@Override
	public boolean checkCompletion(ServerPlayer player) {
		if (this.lampPos == null) return false;
		if (!(player.level() instanceof ServerLevel world)) return false;
		if (!world.isLoaded(this.lampPos)) return false;

		BlockState lamp = world.getBlockState(this.lampPos);
		return lamp.is(Blocks.REDSTONE_LAMP) && lamp.getValue(BlockStateProperties.LIT);
	}

	/**
	 * Flat, open, and clear of whatever the village already built. Nine blocks of
	 * ground with nine blocks of air over it, or nowhere.
	 */
	private static BlockPos findSpot(ServerLevel world, BlockPos near) {
		for (int attempt = 0; attempt < 40; attempt++) {
			int dx = ThreadLocalRandom.current().nextInt(-12, 13);
			int dz = ThreadLocalRandom.current().nextInt(-12, 13);
			int x = near.getX() + dx;
			int z = near.getZ() + dz;
			int y = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos ground = new BlockPos(x, y - 1, z);

			if (isClearRun(world, ground)) return ground;
		}
		return null;
	}

	private static boolean isClearRun(ServerLevel world, BlockPos ground) {
		for (int i = 0; i < SEGMENTS + 3; i++) {
			BlockPos floor = ground.east(i);
			if (!world.getBlockState(floor).isSolidRender()) return false;
			if (!world.getBlockState(floor.above()).isAir()) return false;
			if (!world.getBlockState(floor.above(2)).isAir()) return false;
		}
		return true;
	}

	/** lever - oxidized copper x5 - lamp, in a straight line at head height. */
	private void build(ServerLevel world, BlockPos ground) {
		BlockPos leverBase = ground.above();
		world.setBlockAndUpdate(leverBase, Blocks.STONE_BRICKS.defaultBlockState());

		BlockState lever = Blocks.LEVER.defaultBlockState()
			.setValue(LeverBlock.FACE, AttachFace.FLOOR)
			.setValue(LeverBlock.FACING, Direction.EAST)
			.setValue(LeverBlock.POWERED, true);
		world.setBlockAndUpdate(leverBase.above(), lever);

		// The oxidised variant hangs off the weathering collection, not a Blocks
		// constant, which is the same shape the resistance table above reads.
		Block dead = Blocks.COPPER_BLOCK.weathering().oxidized();
		for (int i = 1; i <= SEGMENTS; i++) {
			world.setBlockAndUpdate(leverBase.east(i), dead.defaultBlockState());
		}

		this.lampPos = leverBase.east(SEGMENTS + 1);
		world.setBlockAndUpdate(this.lampPos, Blocks.REDSTONE_LAMP.defaultBlockState());
	}

	public BlockPos getLampPos() {
		return this.lampPos;
	}
}
