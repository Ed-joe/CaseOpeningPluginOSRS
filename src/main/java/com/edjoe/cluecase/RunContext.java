package com.edjoe.cluecase;

/**
 * What the player did before opening a chest, for sources whose loot depends on it.
 * Only shapes the teaser reel; the real reward always comes from the game.
 */
final class RunContext
{
	/** Barrows brothers in the order of their KILLED varbits. */
	static final String[] BROTHERS = {"Ahrim's", "Dharok's", "Guthan's", "Karil's", "Torag's", "Verac's"};
	/** Moons of Peril, in bit order. */
	static final String[] MOONS = {"Blood Moon", "Blue Moon", "Eclipse Moon"};

	static final int ALL_BROTHERS = 0b111111;
	static final int ALL_MOONS = 0b111;
	static final int MAX_REWARD_POTENTIAL = 1012;

	/** A complete run: all six brothers, maximum reward potential, all three Moons. */
	static final RunContext FULL = new RunContext(ALL_BROTHERS, MAX_REWARD_POTENTIAL, ALL_MOONS);

	private final int brothersMask;
	private final int rewardPotential;
	private final int moonsMask;

	private RunContext(int brothersMask, int rewardPotential, int moonsMask)
	{
		this.brothersMask = brothersMask & ALL_BROTHERS;
		this.rewardPotential = Math.max(1, Math.min(MAX_REWARD_POTENTIAL, rewardPotential));
		this.moonsMask = moonsMask & ALL_MOONS;
	}

	/**
	 * @param brothersMask bit i set when {@link #BROTHERS}[i] was killed
	 * @param monsterCombatSum combat levels of everything killed (capped at 1000 by the game)
	 */
	static RunContext barrows(int brothersMask, int monsterCombatSum)
	{
		int brothers = Integer.bitCount(brothersMask & ALL_BROTHERS);
		return new RunContext(brothersMask, Math.min(1000, monsterCombatSum) + 2 * brothers, ALL_MOONS);
	}

	/** @param moonsMask bit i set when {@link #MOONS}[i] was defeated */
	static RunContext lunar(int moonsMask)
	{
		return new RunContext(ALL_BROTHERS, MAX_REWARD_POTENTIAL, moonsMask == 0 ? ALL_MOONS : moonsMask);
	}

	boolean killedBrother(int index) { return (brothersMask & (1 << index)) != 0; }
	int brothersKilled() { return Integer.bitCount(brothersMask); }
	int rewardPotential() { return rewardPotential; }
	boolean defeatedMoon(int index) { return (moonsMask & (1 << index)) != 0; }
	int moonsDefeated() { return Integer.bitCount(moonsMask); }

	@Override
	public String toString()
	{
		return "RunContext{brothers=" + Integer.toBinaryString(brothersMask) + ", rewardPotential="
			+ rewardPotential + ", moons=" + Integer.toBinaryString(moonsMask) + "}";
	}
}
