package com.edjoe.cluecase;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

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
}
