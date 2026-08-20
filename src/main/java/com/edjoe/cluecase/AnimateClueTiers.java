package com.edjoe.cluecase;

enum AnimateClueTiers
{
	ALL("All tiers", 0),
	MEDIUM_PLUS("Medium and above", 2),
	HARD_PLUS("Hard and above", 3),
	ELITE_PLUS("Elite and above", 4),
	MASTER_ONLY("Master only", 5);

	private final String label;
	private final int minimumTier;

	AnimateClueTiers(String label, int minimumTier)
	{
		this.label = label;
		this.minimumTier = minimumTier;
	}

	boolean includes(int tier) { return tier >= minimumTier; }

	@Override
	public String toString() { return label; }
}
