package com.edjoe.cluecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class ClueLootDataTest
{
	private static final Pattern CSV_FIELD = Pattern.compile("\"((?:[^\"]|\"\")*)\"");

	@Test
	public void beginnerRarityUsesBeginnerTableDistribution()
	{
		assertEquals(LootRarity.RARE_SPECIAL, ClueLootSimulator.rarityFor(
			Collections.singletonList(new Loot(23285, 1)),
			ItemID.TRAIL_REWARD_CASKET_BEGINNER));
	}

	@Test
	public void hardClueUniqueOutranksGenericEquipment()
	{
		LootRarity navyCavalier = ClueLootSimulator.rarityFor(
			Collections.singletonList(new Loot(12325, 1)),
			ItemID.TRAIL_REWARD_CASKET_HARD);
		LootRarity blackDhide = ClueLootSimulator.rarityFor(
			Collections.singletonList(new Loot(2503, 2)),
			ItemID.TRAIL_REWARD_CASKET_HARD);
		assertTrue(navyCavalier.ordinal() > blackDhide.ordinal());
	}

	@Test
	public void totalGeValueThresholdsAreTierSpecificAndOrdered()
	{
		long[] beginner = ClueLootSimulator.valueThresholdsFor(
			ItemID.TRAIL_REWARD_CASKET_BEGINNER);
		long[] hard = ClueLootSimulator.valueThresholdsFor(
			ItemID.TRAIL_REWARD_CASKET_HARD);
		assertTrue(beginner[0] < beginner[1]);
		assertTrue(beginner[1] < beginner[2]);
		assertTrue(beginner[2] < beginner[3]);
		assertTrue(beginner[3] <= 465_000L);
		assertTrue(hard[3] > beginner[3]);
	}

	@Test
	public void everyTierCanGenerateContentsMatchingEveryCaseColor()
	{
		int[] caskets = {
			ItemID.TRAIL_REWARD_CASKET_BEGINNER,
			ItemID.TRAIL_REWARD_CASKET_EASY,
			ItemID.TRAIL_REWARD_CASKET_MEDIUM,
			ItemID.TRAIL_REWARD_CASKET_HARD,
			ItemID.TRAIL_REWARD_CASKET_ELITE,
			ItemID.TRAIL_REWARD_CASKET_MASTER
		};
		LootRarity[] caseColors = {
			LootRarity.MIL_SPEC,
			LootRarity.RESTRICTED,
			LootRarity.CLASSIFIED,
			LootRarity.COVERT,
			LootRarity.RARE_SPECIAL
		};
		for (int casket : caskets)
		{
			for (LootRarity color : caseColors)
			{
				for (int attempt = 0; attempt < 20; attempt++)
				{
					assertEquals(color, ClueLootSimulator.rarityFor(
						ClueLootSimulator.generate(casket, color), casket));
				}
			}
		}
	}

	@Test
	public void everyTierContainsOneCompletePerRollDistribution() throws Exception
	{
		InputStream stream = getClass().getResourceAsStream("/clue-loot.csv");
		assertNotNull(stream);
		Map<String, Double> probabilityMass = new HashMap<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
		{
			reader.readLine();
			String line;
			while ((line = reader.readLine()) != null)
			{
				Matcher fields = CSV_FIELD.matcher(line);
				String tier = null;
				String weight = null;
				for (int field = 0; fields.find(); field++)
				{
					if (field == 0) { tier = fields.group(1); }
					if (field == 6) { weight = fields.group(1); }
				}
				assertNotNull(tier);
				assertNotNull(weight);
				probabilityMass.merge(tier, Double.parseDouble(weight), Double::sum);
			}
		}

		for (String tier : new String[]{"beginner", "easy", "medium", "hard", "elite", "master"})
		{
			assertTrue("Missing " + tier + " loot", probabilityMass.containsKey(tier));
			assertEquals("Incomplete " + tier + " table", 1.0, probabilityMass.get(tier), 0.001);
		}
	}

	@Test
	public void everyChestCanGenerateContentsMatchingEveryCaseColor()
	{
		LootRarity[] caseColors = {
			LootRarity.MIL_SPEC,
			LootRarity.RESTRICTED,
			LootRarity.CLASSIFIED,
			LootRarity.COVERT,
			LootRarity.RARE_SPECIAL
		};
		for (LootSource source : LootSource.values())
		{
			if (source.isClue())
			{
				continue;
			}
			assertTrue("Missing loot table for " + source, ClueLootSimulator.hasTable(source));
			for (LootRarity color : caseColors)
			{
				for (int attempt = 0; attempt < 20; attempt++)
				{
					assertEquals(source + " " + color, color, ClueLootSimulator.rarityFor(
						ClueLootSimulator.generate(source, color), source));
				}
			}
		}
	}

	@Test
	public void raidMegaRaresAreGold()
	{
		assertEquals(LootRarity.RARE_SPECIAL, ClueLootSimulator.rarityFor(
			Collections.singletonList(new Loot(20997, 1)), LootSource.CHAMBERS_OF_XERIC)); // Twisted bow
		assertEquals(LootRarity.RARE_SPECIAL, ClueLootSimulator.rarityFor(
			Collections.singletonList(new Loot(27277, 1)), LootSource.TOMBS_OF_AMASCUT)); // Tumeken's shadow
	}

	@Test
	public void raidUniquesReplaceCommonLoot()
	{
		for (int attempt = 0; attempt < 50; attempt++)
		{
			assertEquals(1, ClueLootSimulator.generate(
				LootSource.THEATRE_OF_BLOOD, LootRarity.RARE_SPECIAL).size());
		}
	}

	@Test
	public void barrowsEquipmentOutranksRunes()
	{
		LootRarity dharokHelm = ClueLootSimulator.rarityFor(
			Collections.singletonList(new Loot(4716, 1)), LootSource.BARROWS);
		LootRarity deathRunes = ClueLootSimulator.rarityFor(
			Collections.singletonList(new Loot(560, 75)), LootSource.BARROWS);
		assertTrue(dharokHelm.ordinal() >= LootRarity.CLASSIFIED.ordinal());
		assertTrue(dharokHelm.ordinal() > deathRunes.ordinal());
	}

	private static Map<Integer, String> chestSections(String source) throws Exception
	{
		Map<Integer, String> sections = new HashMap<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
			ClueLootDataTest.class.getResourceAsStream("/chest-loot.csv"), StandardCharsets.UTF_8)))
		{
			reader.readLine();
			String line;
			while ((line = reader.readLine()) != null)
			{
				Matcher fields = CSV_FIELD.matcher(line);
				String[] values = new String[8];
				for (int field = 0; fields.find() && field < values.length; field++)
				{
					values[field] = fields.group(1);
				}
				if (source.equals(values[0]))
				{
					sections.put(Integer.parseInt(values[2]), values[1]);
				}
			}
		}
		return sections;
	}

	@Test
	public void barrowsRollsAndUniquesFollowBrothersKilled() throws Exception
	{
		Map<Integer, String> sections = chestSections("barrows");
		RunContext ahrimOnly = RunContext.barrows(0b000001, 1000);
		for (int sample = 0; sample < 20_000; sample++)
		{
			List<Loot> loot = ClueLootSimulator.generate(LootSource.BARROWS, null, ahrimOnly);
			assertTrue("1 brother means at most 2 rolls", loot.size() <= 2);
			for (Loot reward : loot)
			{
				String section = sections.get(reward.getItemId());
				assertTrue("Only Ahrim's pieces are possible: " + section,
					!section.endsWith("uniques") || section.startsWith("Ahrim's"));
			}
		}
		// Rare-band reel tiles still only use Ahrim's pieces.
		for (int sample = 0; sample < 50; sample++)
		{
			for (Loot reward : ClueLootSimulator.generate(LootSource.BARROWS, LootRarity.RARE_SPECIAL, ahrimOnly))
			{
				String section = sections.get(reward.getItemId());
				assertTrue(!section.endsWith("uniques") || section.startsWith("Ahrim's"));
			}
		}
	}

	@Test
	public void lowBarrowsRewardPotentialOnlyGivesCoins()
	{
		RunContext noKills = RunContext.barrows(0, 300);
		for (int sample = 0; sample < 2_000; sample++)
		{
			List<Loot> loot = ClueLootSimulator.generate(LootSource.BARROWS, null, noKills);
			assertEquals(1, loot.size());
			assertEquals(995, loot.get(0).getItemId());
		}
	}

	@Test
	public void lunarRollsAndUniquesFollowMoonsDefeated() throws Exception
	{
		Map<Integer, String> sections = chestSections("lunar");
		RunContext bloodOnly = RunContext.lunar(0b001);
		for (int sample = 0; sample < 20_000; sample++)
		{
			List<Loot> loot = ClueLootSimulator.generate(LootSource.LUNAR_CHEST, null, bloodOnly);
			assertEquals("1 Moon means one standard roll or one unique", 1, loot.size());
			String section = sections.get(loot.get(0).getItemId());
			assertTrue(!section.endsWith("uniques") || section.equals("Blood Moon uniques"));
		}
		RunContext allMoons = RunContext.lunar(0b111);
		for (int sample = 0; sample < 20_000; sample++)
		{
			List<Loot> loot = ClueLootSimulator.generate(LootSource.LUNAR_CHEST, null, allMoons);
			boolean anyUnique = false;
			boolean anyStandard = false;
			for (Loot reward : loot)
			{
				if (sections.get(reward.getItemId()).endsWith("uniques")) { anyUnique = true; }
				else { anyStandard = true; }
			}
			assertTrue("A unique replaces the standard loot", !(anyUnique && anyStandard));
			assertTrue(loot.size() <= 6);
		}
	}
}
