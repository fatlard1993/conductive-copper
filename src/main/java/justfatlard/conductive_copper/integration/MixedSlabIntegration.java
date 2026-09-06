package justfatlard.conductive_copper.integration;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Copper that has been built into a mixed slab.
 *
 * <p>A cut copper slab conducts on its own, so putting one in a mixed slab and having the signal
 * stop there is a surprise rather than a missing feature - the copper is still there and still
 * touching. This asks the mixed slab what it is made of.
 *
 * <p>Reached by reflection behind a mod-loaded check, the way the rest of the suite handles a
 * neighbour it does not require. Nothing outside the guarded methods names a type from that mod, so
 * a server without it never loads a class it does not have.
 */
public final class MixedSlabIntegration {
	private MixedSlabIntegration() {}

	private static final org.slf4j.Logger LOGGER =
		org.slf4j.LoggerFactory.getLogger("conductive-copper/mixed-slabs");

	private static final boolean AVAILABLE = FabricLoader.getInstance().isModLoaded("mixed-slabs-justfatlard");

	/**
	 * Bound once. This sits behind {@code isConductor}, which redstone calls hard enough
	 * that looking a method up per call would show.
	 */
	private static MethodHandle halves;
	private static boolean bound = false;

	/** The blocks inside a mixed slab, or an empty list for anything else. */
	@SuppressWarnings("unchecked")
	public static List<Block> halvesOf(BlockState state) {
		if (!AVAILABLE) return List.of();

		MethodHandle handle = handle();
		if (handle == null) return List.of();

		try {
			return (List<Block>) handle.invokeExact(state);
		} catch (Throwable t) {
			return List.of();
		}
	}

	private static MethodHandle handle() {
		if (bound) return halves;
		bound = true;

		try {
			Class<?> api = Class.forName("justfatlard.mixed_slabs.MixedSlabsApi");
			halves = MethodHandles.lookup().findStatic(api, "halves",
				java.lang.invoke.MethodType.methodType(List.class, BlockState.class));
		} catch (ReflectiveOperationException e) {
			// Installed but not the shape we expected - the feature is simply absent.
			LOGGER.warn("mixed-slabs is present but its API did not match; "
				+ "copper inside a mixed slab will not conduct", e);
		}
		return halves;
	}
}
