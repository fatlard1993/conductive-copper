package justfatlard.conductive_copper.integration;

import justfatlard.block_tip.api.BlockTipApi;
import justfatlard.conductive_copper.ConductiveCopper;

/**
 * What this conductor costs a signal passing through it, and what it is carrying now.
 *
 * <p>The number is the whole mechanic and it is invisible twice over: metal
 * carries redstone at all, which nothing says, and each stage of copper's weathering
 * eats more of it, which nothing shows. A player looking at a green block has no way
 * to know it is the reason their lamp is dark.
 *
 * <p>The live reading is the other half. Dust shows its level on the card already;
 * a conductor has no POWER property to read, so the walk is run for it on the spot.
 * Said first, because it is the number a player debugging a dark lamp came to see.
 */
public final class CopperTipRegistration {
	private CopperTipRegistration() {}

	/** block-tip's glyph for a signal strength, matched so the number reads the same everywhere. */
	private static final String POWER = "\u26A1";

	public static void register() {
		BlockTipApi.describe((level, pos, state, player) -> {
			if (!ConductiveCopper.isConductor(state)) return null;

			return POWER + " " + ConductiveCopper.signalAt(level, pos) + "/15";
		});

		BlockTipApi.describe((level, pos, state, player) -> {
			Integer resistance = ConductiveCopper.resistanceOf(state);
			if (resistance == null) return null;

			return resistance == 0
				? "Carries redstone, lossless"
				: "Carries redstone, -" + resistance + " a block";
		});
	}
}
