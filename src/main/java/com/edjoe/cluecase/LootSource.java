package com.edjoe.cluecase;

import java.util.Locale;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;

/**
 * Everything that can trigger a case-opening reveal: the six clue caskets plus reward
 * chests whose loot is shown in a dedicated interface.
 */
enum LootSource
{
	BEGINNER_CLUE("beginner", "Beginner clue", ItemID.TRAIL_REWARD_CASKET_BEGINNER, -1, -1,
		"reward-casket-beginner", 1, 3, UniqueMode.ROLLED, 0.0),
	EASY_CLUE("easy", "Easy clue", ItemID.TRAIL_REWARD_CASKET_EASY, -1, -1,
		"reward-casket-easy", 2, 4, UniqueMode.ROLLED, 0.0),
	MEDIUM_CLUE("medium", "Medium clue", ItemID.TRAIL_REWARD_CASKET_MEDIUM, -1, -1,
		"reward-casket-medium", 3, 5, UniqueMode.ROLLED, 0.0),
	HARD_CLUE("hard", "Hard clue", ItemID.TRAIL_REWARD_CASKET_HARD, -1, -1,
		"reward-casket-hard", 4, 6, UniqueMode.ROLLED, 0.0),
	ELITE_CLUE("elite", "Elite clue", ItemID.TRAIL_REWARD_CASKET_ELITE, -1, -1,
		"reward-casket-elite", 4, 6, UniqueMode.ROLLED, 0.0),
	MASTER_CLUE("master", "Master clue", ItemID.TRAIL_REWARD_CASKET_MASTER, -1, -1,
		"reward-casket-master", 5, 7, UniqueMode.ROLLED, 0.0),

	// Barrows and the Lunar Chest roll from the player's run (brothers killed, reward
	// potential, Moons defeated); see RunContext. The roll counts here are unused.
	BARROWS("barrows", "Barrows chest", -1, InterfaceID.BARROWS_REWARD, InventoryID.TRAIL_REWARDINV,
		"reward-chest-barrows", 7, 7, UniqueMode.ROLLED, 0.0),
	LUNAR_CHEST("lunar", "Lunar Chest", -1, InterfaceID.PMOON_REWARD, InventoryID.PMOON_REWARDINV,
		"reward-chest-lunar", 6, 6, UniqueMode.EXCLUSIVE, 0.0),
	// Raids: a purple replaces the common loot entirely. Unique chances are a typical
	// personal rate and only shape teaser reels; they never affect real loot.
	CHAMBERS_OF_XERIC("cox", "Chambers of Xeric", -1, InterfaceID.RAIDS_REWARDS, InventoryID.RAIDS_REWARDS,
		"reward-chest-cox", 2, 2, UniqueMode.EXCLUSIVE, 0.06),
	THEATRE_OF_BLOOD("tob", "Theatre of Blood", -1, InterfaceID.TOB_CHESTS, InventoryID.TOB_CHESTS,
		"reward-chest-tob", 3, 3, UniqueMode.EXCLUSIVE, 0.10),
	TOMBS_OF_AMASCUT("toa", "Tombs of Amascut", -1, InterfaceID.TOA_CHESTS, InventoryID.TOA_CHESTS,
		"reward-chest-toa", 3, 3, UniqueMode.EXCLUSIVE, 0.06);

	/** How a source's unique items relate to its ordinary rolls. */
	enum UniqueMode
	{
		/** Uniques are ordinary entries on the per-roll table. */
		ROLLED,
		/** Either a single unique, or the usual rolls from the common table. */
		EXCLUSIVE
	}

	private final String key;
	private final String displayName;
	private final int casketItemId;
	private final int interfaceGroupId;
	private final int inventoryId;
	private final String spriteSheet;
	private final int minimumRolls;
	private final int maximumRolls;
	private final UniqueMode uniqueMode;
	private final double exclusiveUniqueChance;

	LootSource(String key, String displayName, int casketItemId, int interfaceGroupId, int inventoryId,
		String spriteSheet, int minimumRolls, int maximumRolls, UniqueMode uniqueMode,
		double exclusiveUniqueChance)
	{
		this.key = key;
		this.displayName = displayName;
		this.casketItemId = casketItemId;
		this.interfaceGroupId = interfaceGroupId;
		this.inventoryId = inventoryId;
		this.spriteSheet = spriteSheet;
		this.minimumRolls = minimumRolls;
		this.maximumRolls = maximumRolls;
		this.uniqueMode = uniqueMode;
		this.exclusiveUniqueChance = exclusiveUniqueChance;
	}

	String getKey() { return key; }
	String getDisplayName() { return displayName; }
	int getCasketItemId() { return casketItemId; }
	int getInterfaceGroupId() { return interfaceGroupId; }
	int getInventoryId() { return inventoryId; }
	String getSpriteSheetResource() { return "/" + spriteSheet + "-sprite-sheet.png"; }
	int getMinimumRolls() { return minimumRolls; }
	int getMaximumRolls() { return maximumRolls; }
	UniqueMode getUniqueMode() { return uniqueMode; }
	double getExclusiveUniqueChance() { return exclusiveUniqueChance; }

	boolean isClue() { return casketItemId > 0; }

	/** Clue tier index used by {@link AnimateClueTiers}, or -1 for chests. */
	int clueTier() { return isClue() ? ordinal() : -1; }

	static LootSource forCasket(int itemId)
	{
		for (LootSource source : values())
		{
			if (source.isClue() && source.casketItemId == itemId)
			{
				return source;
			}
		}
		return null;
	}

	static LootSource forInterface(int groupId)
	{
		for (LootSource source : values())
		{
			if (!source.isClue() && source.interfaceGroupId == groupId)
			{
				return source;
			}
		}
		return null;
	}

	static LootSource forKey(String key)
	{
		String normalized = key.toLowerCase(Locale.ROOT);
		for (LootSource source : values())
		{
			if (source.key.equals(normalized))
			{
				return source;
			}
		}
		return null;
	}
}
