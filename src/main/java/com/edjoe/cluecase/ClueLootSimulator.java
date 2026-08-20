package com.edjoe.cluecase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.gameval.ItemID;

/** Generates teaser caskets from the flattened OSRS Wiki tables bundled in clue-loot.csv. */
final class ClueLootSimulator
{
	private static final String RESOURCE = "/clue-loot.csv";
	private static final double TABLE_RARITY_WEIGHT = 0.40;
	private static final double GE_VALUE_WEIGHT = 0.60;
	private static final int MAX_TARGET_GENERATION_ATTEMPTS = 1_000;
	private static final Map<String, Table> TABLES = loadTables();

	private ClueLootSimulator() {}

	static List<Loot> generate(int casketItemId)
	{
		return generate(casketItemId, null);
	}

	static List<Loot> generate(int casketItemId, LootRarity requestedRarity)
	{
		for (int attempt = 0; attempt < MAX_TARGET_GENERATION_ATTEMPTS; attempt++)
		{
			List<Loot> loot = generateCandidate(casketItemId, requestedRarity);
			if (requestedRarity == null || rarityFor(loot, casketItemId) == requestedRarity)
			{
				return loot;
			}
		}
		throw new IllegalStateException("Unable to generate " + requestedRarity
			+ " teaser contents after " + MAX_TARGET_GENERATION_ATTEMPTS + " attempts");
	}

	private static List<Loot> generateCandidate(int casketItemId, LootRarity requestedRarity)
	{
		Tier tier = tierFor(casketItemId);
		Table table = TABLES.get(tier.name);
		if (table == null || table.entries.isEmpty())
		{
			throw new IllegalStateException("No clue loot data loaded for " + tier.name);
		}
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int rolls = random.nextInt(tier.minimumRolls, tier.maximumRolls + 1);
		Map<Integer, Integer> combined = new LinkedHashMap<>();
		LootRarity selectedRarity = requestedRarity == null
			? null : table.requireAvailableRarity(requestedRarity);
		for (int roll = 0; roll < rolls; roll++)
		{
			// Guarantee one reward from the requested band. Other rolls may be
			// more common, but never rarer, so the bundle retains that exact rarity.
			Entry entry = selectedRarity == null
				? table.pick(random.nextDouble(table.totalWeight))
				: table.pickForRarity(selectedRarity, roll == 0, random);
			int quantity = random.nextInt(entry.minimumQuantity, entry.maximumQuantity + 1);
			combined.merge(entry.itemId, quantity, Integer::sum);
		}
		List<Loot> loot = new ArrayList<>();
		combined.forEach((itemId, quantity) -> loot.add(new Loot(itemId, quantity)));
		return loot;
	}

	static LootRarity rarityFor(List<Loot> loot, int casketItemId)
	{
		Table table = TABLES.get(tierFor(casketItemId).name);
		LootRarity rarest = LootRarity.MIL_SPEC;
		long totalGeValue = 0L;
		for (Loot reward : loot)
		{
			LootRarity itemRarity = table.rarityByItem.getOrDefault(
				reward.getItemId(), LootRarity.MIL_SPEC);
			if (itemRarity.ordinal() > rarest.ordinal())
			{
				rarest = itemRarity;
			}
			long unitPrice = table.gePriceByItem.getOrDefault(reward.getItemId(), 0L);
			long itemValue;
			try
			{
				itemValue = Math.multiplyExact(unitPrice, (long) reward.getQuantity());
				totalGeValue = Math.addExact(totalGeValue, itemValue);
			}
			catch (ArithmeticException ignored)
			{
				totalGeValue = Long.MAX_VALUE;
			}
		}
		LootRarity valueFloor = table.rarityForTotalValue(totalGeValue);
		return valueFloor.ordinal() > rarest.ordinal() ? valueFloor : rarest;
	}

	static long[] valueThresholdsFor(int casketItemId)
	{
		Table table = TABLES.get(tierFor(casketItemId).name);
		return Arrays.copyOf(table.valueThresholds, table.valueThresholds.length);
	}

	private static Map<String, Table> loadTables()
	{
		Map<String, Table> tables = new HashMap<>();
		try (InputStream stream = ClueLootSimulator.class.getResourceAsStream(RESOURCE))
		{
			if (stream == null) { throw new IllegalStateException("Missing resource " + RESOURCE); }
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
			{
				reader.readLine();
				String line;
				while ((line = reader.readLine()) != null)
				{
					List<String> fields = parseCsvLine(line);
					if (fields.size() < 8) { continue; }
					Entry entry = new Entry(fields.get(1), Integer.parseInt(fields.get(2)),
						Integer.parseInt(fields.get(4)), Integer.parseInt(fields.get(5)),
						Double.parseDouble(fields.get(6)), Long.parseLong(fields.get(7)));
					tables.computeIfAbsent(fields.get(0), ignored -> new Table()).add(entry);
				}
			}
		}
		catch (IOException | NumberFormatException exception)
		{
			throw new IllegalStateException("Unable to load " + RESOURCE, exception);
		}
		for (Map.Entry<String, Table> loadedTable : tables.entrySet())
		{
			Table table = loadedTable.getValue();
			table.buildRarityBands();
			Tier tier = tierForName(loadedTable.getKey());
			table.buildValueThresholds(tier.minimumRolls, tier.maximumRolls,
				loadedTable.getKey().hashCode());
		}
		return tables;
	}

	private static List<String> parseCsvLine(String line)
	{
		List<String> fields = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		for (int index = 0; index < line.length(); index++)
		{
			char character = line.charAt(index);
			if (character == '"')
			{
				if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"')
				{
					field.append('"'); index++;
				}
				else { quoted = !quoted; }
			}
			else if (character == ',' && !quoted)
			{
				fields.add(field.toString()); field.setLength(0);
			}
			else { field.append(character); }
		}
		fields.add(field.toString());
		return fields;
	}

	private static Tier tierFor(int casketItemId)
	{
		switch (casketItemId)
		{
			case ItemID.TRAIL_REWARD_CASKET_BEGINNER: return new Tier("beginner", 1, 3);
			case ItemID.TRAIL_REWARD_CASKET_EASY: return new Tier("easy", 2, 4);
			case ItemID.TRAIL_REWARD_CASKET_MEDIUM: return new Tier("medium", 3, 5);
			case ItemID.TRAIL_REWARD_CASKET_HARD: return new Tier("hard", 4, 6);
			case ItemID.TRAIL_REWARD_CASKET_ELITE: return new Tier("elite", 4, 6);
			case ItemID.TRAIL_REWARD_CASKET_MASTER: return new Tier("master", 5, 7);
			default: return new Tier("hard", 4, 6);
		}
	}

	private static Tier tierForName(String name)
	{
		switch (name)
		{
			case "beginner": return new Tier(name, 1, 3);
			case "easy": return new Tier(name, 2, 4);
			case "medium": return new Tier(name, 3, 5);
			case "hard": return new Tier(name, 4, 6);
			case "elite": return new Tier(name, 4, 6);
			case "master": return new Tier(name, 5, 7);
			default: throw new IllegalArgumentException("Unknown clue tier " + name);
		}
	}

	private static final class Tier
	{
		private final String name; private final int minimumRolls; private final int maximumRolls;
		private Tier(String name, int minimumRolls, int maximumRolls)
		{
			this.name = name; this.minimumRolls = minimumRolls; this.maximumRolls = maximumRolls;
		}
	}

	private static final class Table
	{
		private final List<Entry> entries = new ArrayList<>();
		private final Map<Integer, Double> probabilityByItem = new HashMap<>();
		private final Map<Integer, String> sectionByItem = new HashMap<>();
		private final Map<Integer, Long> gePriceByItem = new HashMap<>();
		private final Map<Integer, LootRarity> rarityByItem = new HashMap<>();
		private final long[] valueThresholds = new long[4];
		private double totalWeight;
		private void add(Entry entry)
		{
			entries.add(entry);
			totalWeight += entry.weight;
			probabilityByItem.merge(entry.itemId, entry.weight, Double::sum);
			sectionByItem.putIfAbsent(entry.itemId, entry.section);
			gePriceByItem.put(entry.itemId, entry.gePrice);
		}
		private Entry pick(double target)
		{
			double cumulative = 0.0;
			for (Entry entry : entries)
			{
				cumulative += entry.weight;
				if (target < cumulative) { return entry; }
			}
			return entries.get(entries.size() - 1);
		}

		private LootRarity rarityOf(Entry entry)
		{
			return rarityByItem.getOrDefault(entry.itemId, LootRarity.MIL_SPEC);
		}

		private void buildRarityBands()
		{
			Map<String, List<Integer>> uniqueItemsBySection = new HashMap<>();
			List<Integer> ordinaryItems = new ArrayList<>();
			for (Map.Entry<Integer, Double> item : probabilityByItem.entrySet())
			{
				String section = sectionByItem.get(item.getKey());
				if (isMegaRare(section))
				{
					continue;
				}
				List<Integer> items = isClueUnique(section)
					? uniqueItemsBySection.computeIfAbsent(section, ignored -> new ArrayList<>())
					: ordinaryItems;
				items.add(item.getKey());
			}
			assignRelativeRarities(ordinaryItems, false);
			for (List<Integer> items : uniqueItemsBySection.values())
			{
				assignRelativeRarities(items, true);
			}

			for (Map.Entry<Integer, Double> item : probabilityByItem.entrySet())
			{
				String section = sectionByItem.get(item.getKey());
				if (isMegaRare(section))
				{
					rarityByItem.put(item.getKey(), LootRarity.RARE_SPECIAL);
					continue;
				}
			}
		}

		private void assignRelativeRarities(List<Integer> itemIds, boolean allowGold)
		{
			if (itemIds.isEmpty()) { return; }
			List<Double> probabilityLevels = new ArrayList<>();
			List<Long> priceLevels = new ArrayList<>();
			for (int itemId : itemIds)
			{
				double probability = probabilityByItem.get(itemId);
				long price = gePriceByItem.getOrDefault(itemId, 0L);
				if (!probabilityLevels.contains(probability)) { probabilityLevels.add(probability); }
				if (!priceLevels.contains(price)) { priceLevels.add(price); }
			}
			probabilityLevels.sort(java.util.Collections.reverseOrder());
			java.util.Collections.sort(priceLevels);
			Map<Integer, Double> scores = new HashMap<>();
			List<Double> scoreLevels = new ArrayList<>();
			for (int itemId : itemIds)
			{
				double probabilityRank = normalizedRank(probabilityLevels,
					probabilityByItem.get(itemId));
				double priceRank = normalizedRank(priceLevels,
					gePriceByItem.getOrDefault(itemId, 0L));
				double score = probabilityRank * TABLE_RARITY_WEIGHT
					+ priceRank * GE_VALUE_WEIGHT;
				scores.put(itemId, score);
				if (!scoreLevels.contains(score)) { scoreLevels.add(score); }
			}
			java.util.Collections.sort(scoreLevels);
			for (int itemId : itemIds)
			{
				double relativeRank = normalizedRank(scoreLevels, scores.get(itemId));
				rarityByItem.put(itemId, rarityForRelativeRank(relativeRank, allowGold));
			}
		}

		private static double normalizedRank(List<?> levels, Object value)
		{
			return levels.size() <= 1 ? 1.0 : levels.indexOf(value) / (double) (levels.size() - 1);
		}

		private static LootRarity rarityForRelativeRank(double relativeRank, boolean allowGold)
		{
			LootRarity[] bands = allowGold
				? new LootRarity[]{LootRarity.CLASSIFIED, LootRarity.COVERT, LootRarity.RARE_SPECIAL}
				: new LootRarity[]{LootRarity.MIL_SPEC, LootRarity.RESTRICTED};
			int band = Math.min(bands.length - 1,
				(int) Math.floor(relativeRank * bands.length));
			return bands[band];
		}

		private static boolean isClueUnique(String section)
		{
			return section != null && section.toLowerCase().contains("clue uniques");
		}

		private static boolean isMegaRare(String section)
		{
			return section != null && section.toLowerCase().contains("mega");
		}

		private void buildValueThresholds(int minimumRolls, int maximumRolls, int seed)
		{
			final int samples = 100_000;
			long[] values = new long[samples];
			Random random = new Random(0x43415345L ^ seed);
			for (int sample = 0; sample < samples; sample++)
			{
				int rolls = minimumRolls + random.nextInt(maximumRolls - minimumRolls + 1);
				long total = 0L;
				for (int roll = 0; roll < rolls; roll++)
				{
					Entry entry = pick(random.nextDouble() * totalWeight);
					int quantity = entry.minimumQuantity
						+ random.nextInt(entry.maximumQuantity - entry.minimumQuantity + 1);
					total += entry.gePrice * (long) quantity;
				}
				values[sample] = total;
			}
			Arrays.sort(values);
			valueThresholds[0] = percentile(values, 0.79923);
			valueThresholds[1] = percentile(values, 0.95908);
			valueThresholds[2] = percentile(values, 0.99105);
			valueThresholds[3] = percentile(values, 0.99744);
		}

		private static long percentile(long[] sortedValues, double percentile)
		{
			return sortedValues[(int) Math.floor(percentile * (sortedValues.length - 1))];
		}

		private LootRarity rarityForTotalValue(long totalGeValue)
		{
			if (totalGeValue >= valueThresholds[3]) { return LootRarity.RARE_SPECIAL; }
			if (totalGeValue >= valueThresholds[2]) { return LootRarity.COVERT; }
			if (totalGeValue >= valueThresholds[1]) { return LootRarity.CLASSIFIED; }
			if (totalGeValue >= valueThresholds[0]) { return LootRarity.RESTRICTED; }
			return LootRarity.MIL_SPEC;
		}

		private LootRarity requireAvailableRarity(LootRarity requested)
		{
			for (Entry entry : entries)
			{
				if (rarityOf(entry) == requested)
				{
					return requested;
				}
			}
			throw new IllegalStateException("No " + requested + " rewards in clue table");
		}

		private Entry pickForRarity(LootRarity rarity, boolean exact, ThreadLocalRandom random)
		{
			double eligibleWeight = 0.0;
			for (Entry entry : entries)
			{
				LootRarity entryRarity = rarityOf(entry);
				if ((exact && entryRarity == rarity)
					|| (!exact && entryRarity.ordinal() <= rarity.ordinal()))
				{
					eligibleWeight += entry.weight;
				}
			}

			double target = random.nextDouble(eligibleWeight);
			double cumulative = 0.0;
			Entry fallback = null;
			for (Entry entry : entries)
			{
				LootRarity entryRarity = rarityOf(entry);
				if ((exact && entryRarity == rarity)
					|| (!exact && entryRarity.ordinal() <= rarity.ordinal()))
				{
					fallback = entry;
					cumulative += entry.weight;
					if (target < cumulative) { return entry; }
				}
			}
			return fallback;
		}
	}

	private static final class Entry
	{
		private final String section; private final int itemId; private final int minimumQuantity; private final int maximumQuantity; private final double weight; private final long gePrice;
		private Entry(String section, int itemId, int minimumQuantity, int maximumQuantity, double weight, long gePrice)
		{
			this.section = section; this.itemId = itemId; this.minimumQuantity = minimumQuantity;
			this.maximumQuantity = maximumQuantity; this.weight = weight;
			this.gePrice = gePrice;
		}
	}
}
