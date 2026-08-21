package com.edjoe.cluecase;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;

/** Plays native Old School RuneScape sound effects through the game client. */
@Singleton
class ClueCaseSounds
{
	// Sound IDs currently have no generated gameval constants in the RuneLite API.
	private static final int BANK_PIN_SUCCESS = 2274;
	private static final int CASKET_THUD = 2109;
	private static final int CLOCK_TICK_SINGLE = 4195;
	private static final int UNIQUE_DROP = 6765;

	private final Client client;
	private final ClueCaseConfig config;

	@Inject
	ClueCaseSounds(Client client, ClueCaseConfig config)
	{
		this.client = client;
		this.config = config;
	}

	void playOpen()
	{
		play(BANK_PIN_SUCCESS);
	}

	void playDrop()
	{
		play(CASKET_THUD);
	}

	void playBounce()
	{
		play(CASKET_THUD);
	}

	void playTick()
	{
		play(CLOCK_TICK_SINGLE);
	}

	void playReward()
	{
		play(UNIQUE_DROP);
	}

	private void play(int soundId)
	{
		if (!config.soundEffects())
		{
			return;
		}
		// The single-argument API follows the in-game Sound Effects slider,
		// including its muted setting.
		client.playSoundEffect(soundId);
		// Layer a second native instance for a modest relative boost without
		// bypassing the game's Sound Effects volume or mute setting.
		client.playSoundEffect(soundId);
	}
}
