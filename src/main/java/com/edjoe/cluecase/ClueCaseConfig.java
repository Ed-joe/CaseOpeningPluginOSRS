package com.edjoe.cluecase;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(ClueCaseConfig.GROUP)
public interface ClueCaseConfig extends Config
{
	String GROUP = "cluecase";

	@ConfigItem(
		keyName = "animateClueTiers",
		name = "Animate clue tiers",
		description = "Choose the lowest clue tier that uses the case-opening animation.",
		position = 0
	)
	default AnimateClueTiers animateClueTiers() { return AnimateClueTiers.ALL; }

	@ConfigItem(
		keyName = "interactionControl",
		name = "Skip and close control",
		description = "Choose which input skips the animation and closes the result.",
		position = 1
	)
	default InteractionControl interactionControl() { return InteractionControl.BOTH; }

	@ConfigItem(
		keyName = "soundEffects",
		name = "Sound effects",
		description = "Play native game sounds during the casket animation and reward reel.",
		position = 2
	)
	default boolean soundEffects()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideChatDuringOpening",
		name = "Hide chat during opening",
		description = "Hide the whole chatbox while the animation plays, so loot messages from the game"
			+ " or other plugins (such as loot value announcements) cannot spoil the reveal.",
		position = 3
	)
	default boolean hideChatDuringOpening()
	{
		return true;
	}

	@ConfigSection(
		name = "Reward chests",
		description = "Play the case-opening reveal for reward chests as well as clue caskets.",
		position = 4
	)
	String chestsSection = "chests";

	@ConfigItem(
		keyName = "animateBarrows",
		name = "Barrows chest",
		description = "Animate loot from the Barrows chest.",
		section = chestsSection,
		position = 5
	)
	default boolean animateBarrows() { return true; }

	@ConfigItem(
		keyName = "animateLunarChest",
		name = "Lunar Chest",
		description = "Animate loot from the Perilous Moons Lunar Chest.",
		section = chestsSection,
		position = 6
	)
	default boolean animateLunarChest() { return true; }

	@ConfigItem(
		keyName = "animateRaids",
		name = "Raid chests",
		description = "Animate loot from the Chambers of Xeric, Theatre of Blood and Tombs of Amascut chests.",
		section = chestsSection,
		position = 7
	)
	default boolean animateRaids() { return true; }
}
