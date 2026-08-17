package justfatlard.conductive_copper.integration;

import justfatlard.conductive_copper.quest.SignalLineQuest;
import justfatlard.village_quests.api.QuestRegistry;
import net.minecraft.server.level.ServerLevel;

/**
 * Offers the broken signal line through village-quests.
 *
 * <p>This class names village-quests classes directly, so it must only ever be
 * loaded behind the isModLoaded guard in the mod's entry point. Same shape as
 * poopsmith's latrine quests.
 *
 * <p>Offered by the two professions with any business near metal, and rarely:
 * it builds something in the world, and a village growing a copper line every
 * afternoon would stop reading as a village.
 */
public final class SignalQuestRegistration {
	private SignalQuestRegistration() {}

	private static final float OFFER_CHANCE = 0.08F;

	public static void register() {
		QuestRegistry.registerProfessionQuest("toolsmith", SignalQuestRegistration::offer);
		QuestRegistry.registerProfessionQuest("armorer", SignalQuestRegistration::offer);
	}

	private static justfatlard.village_quests.quest.VillagerQuest offer(
			net.minecraft.world.entity.npc.villager.Villager villager, String villagerName, int reputation,
			java.util.Random random) {
		if (!(villager.level() instanceof ServerLevel)) return null;
		if (random.nextFloat() > OFFER_CHANCE) return null;

		// Nobody hands a stranger a puzzle about their own broken infrastructure.
		if (reputation < 15) return null;

		return new SignalLineQuest(villagerName, villager.getUUID());
	}
}
