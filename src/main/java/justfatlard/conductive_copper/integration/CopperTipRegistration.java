package justfatlard.conductive_copper.integration;

import justfatlard.block_tip.api.BlockTipApi;
import justfatlard.conductive_copper.ConductiveCopper;

/**
 * What this copper costs a signal passing through it.
 *
 * <p>The number is the whole mechanic and it is invisible twice over: copper
 * carries redstone at all, which nothing says, and each stage of weathering eats
 * more of it, which nothing shows. A player looking at a green block has no way
 * to know it is the reason their lamp is dark.
 */
public final class CopperTipRegistration {
	private CopperTipRegistration() {}

	public static void register() {
		BlockTipApi.describe((level, pos, state, player) -> {
			Integer resistance = ConductiveCopper.resistanceOf(state.getBlock());
			if (resistance == null) return null;

			return resistance == 0
				? "Carries redstone, lossless"
				: "Carries redstone, -" + resistance + " a block";
		});
	}
}
