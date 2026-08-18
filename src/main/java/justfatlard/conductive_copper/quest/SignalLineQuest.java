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
 * A signal that has to go up, and does not make it.
 *
 * <p>Copper carrying redstone sideways is a curiosity: dust already does that,
 * and cheaper. Copper carrying it <em>upward</em> is the reason the mod exists,
 * because dust cannot climb at all and the vanilla answer is a staircase of
 * repeaters taking up a room. So the puzzle is a mast: switch at the bottom,
 * lamp at the top, and nothing in between but copper.
 *
 * <p>The second half is that oxidation costs signal, which nothing anywhere
 * says. Five oxidized blocks eat exactly the fifteen a lever makes, so the lamp
 * sits at zero: not dim, dark, which reads as broken rather than nearly working.
 * Scraping one block with an axe moves a number that was not moving, and that is
 * the moment both halves explain themselves at once.
 *
 * <p>Raised on acceptance, in front of the player, so the villager talks about it
 * in the present tense. Nobody is asked to repair a mast that appeared while they
 * were being told about it.
 */
public class SignalLineQuest extends VillagerQuest {
	/** Oxidized costs 3 a block, and a lever makes 15. Five of them is a dead mast. */
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
			this.requesterName + ": \"Right - watch. Switch down here, lamp up top, copper between. "
				+ "Redstone dust will not climb, so this is the only way I know to do it. *throws the lever* ...And nothing. Every time.\"",
			this.requesterName + ": \"I am putting a light on a mast so it can be seen from the fields. "
				+ "The copper is meant to carry it up. I have just this minute finished and it does not work.\"",
			this.requesterName + ": \"Up. That is the trick of copper - it goes up, and dust never has. "
				+ "So why is my lamp dark? I have built it exactly as I was told.\""
		};
		return lines[rng.nextInt(lines.length)];
	}

	@Override
	public String getObjective() {
		return this.lampPos == null
			? "find the dead mast near " + this.requesterName
			: "get the signal up to the lamp at " + this.lampPos.getX() + ", " + this.lampPos.getY() + ", " + this.lampPos.getZ();
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
	 * Somewhere a mast can stand: solid underfoot, and clear all the way up.
	 *
	 * <p>The old version wanted a runway of nine blocks in a row, which a village
	 * full of houses often has not got. A column needs one square of ground and
	 * some sky, which almost anywhere has.
	 */
	private static BlockPos findSpot(ServerLevel world, BlockPos near) {
		for (int attempt = 0; attempt < 40; attempt++) {
			int dx = ThreadLocalRandom.current().nextInt(-12, 13);
			int dz = ThreadLocalRandom.current().nextInt(-12, 13);
			int x = near.getX() + dx;
			int z = near.getZ() + dz;
			int y = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos ground = new BlockPos(x, y - 1, z);

			if (isClearMast(world, ground)) return ground;
		}
		return null;
	}

	private static boolean isClearMast(ServerLevel world, BlockPos ground) {
		if (!world.getBlockState(ground).isSolidRender()) return false;

		// The column, the lamp on top of it, and the square the lever hangs in.
		for (int i = 1; i <= SEGMENTS + 1; i++) {
			if (!world.getBlockState(ground.above(i)).isAir()) return false;
		}
		return world.getBlockState(ground.above().south()).isAir();
	}

	/** A mast: five oxidized copper standing up, a lever on the bottom of it, a lamp on top. */
	private void build(ServerLevel world, BlockPos ground) {
		// The oxidised variant hangs off the weathering collection, not a Blocks
		// constant, which is the same shape the resistance table reads.
		Block dead = Blocks.COPPER_BLOCK.weathering().oxidized();

		for (int i = 1; i <= SEGMENTS; i++) {
			world.setBlockAndUpdate(ground.above(i), dead.defaultBlockState());
		}

		// On the side of the lowest block, so it powers the copper directly and the
		// only path to the lamp is up through the mast.
		BlockState lever = Blocks.LEVER.defaultBlockState()
			.setValue(LeverBlock.FACE, AttachFace.WALL)
			.setValue(LeverBlock.FACING, Direction.SOUTH)
			.setValue(LeverBlock.POWERED, true);
		world.setBlockAndUpdate(ground.above().south(), lever);

		this.lampPos = ground.above(SEGMENTS + 1);
		world.setBlockAndUpdate(this.lampPos, Blocks.REDSTONE_LAMP.defaultBlockState());
	}

	public BlockPos getLampPos() {
		return this.lampPos;
	}
}
