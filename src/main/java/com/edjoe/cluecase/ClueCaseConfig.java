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
		keyName = "spaceControl",
		name = "Space skips and closes",
		description = "Allow Space to skip the animation or close the result.",
		position = 1
	)
	default boolean spaceControl() { return true; }

	@ConfigItem(
		keyName = "leftClickControl",
		name = "Left-click skips and closes",
		description = "Allow left-click to skip the animation or close the result.",
		position = 2
	)
	default boolean leftClickControl() { return true; }

	@ConfigItem(
		keyName = "soundEffects",
		name = "Sound effects",
		description = "Play native game sounds during the casket animation and reward reel.",
		position = 3
	)
	default boolean soundEffects()
	{
		return true;
	}
}
