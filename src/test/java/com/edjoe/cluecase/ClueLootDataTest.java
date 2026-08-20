package com.edjoe.cluecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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
}
