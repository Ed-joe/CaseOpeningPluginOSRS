package com.edjoe.cluecase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.gameval.ItemID;

/**
 * Generates teaser rewards from the flattened OSRS Wiki tables bundled in clue-loot.csv
 * (clue caskets) and chest-loot.csv (Barrows, Lunar Chest and raid chests).
 */
final class ClueLootSimulator
{
	private static final String[] RESOURCES = {"/clue-loot.csv", "/chest-loot.csv"};
	private static final double TABLE_RARITY_WEIGHT = 0.40;
	private static final double GE_VALUE_WEIGHT = 0.60;
	private static final int MAX_TARGET_GENERATION_ATTEMPTS = 1_000;
	/** Standard-loot rolls for 0..3 Moons defeated (OSRS Wiki, Lunar Chest loot mechanics). */
	private static final int[] LUNAR_ROLLS = {1, 1, 3, 6};
	private static final double LUNAR_UNIQUE_CHANCE = 1.0 / 56.0;

	/**
	 * Barrows common items by reward potential (OSRS Wiki, "Barrows drop mechanics"): a roll in
	 * 1..potential lands in one band; quantity reaches its maximum at the full-quantity value.
	 */
	private static final BarrowsBand[] BARROWS_BANDS = {
		new BarrowsBand(ItemID.COINS, 1, 380, 380),
		new BarrowsBand(ItemID.MINDRUNE, 381, 505, 505),
		new BarrowsBand(ItemID.CHAOSRUNE, 506, 630, 630),
		new BarrowsBand(ItemID.DEATHRUNE, 631, 755, 755),
		new BarrowsBand(ItemID.BLOODRUNE, 756, 880, 880),
		new BarrowsBand(ItemID.BARROWS_KARIL_AMMO, 881, 1005, 1005),
		new BarrowsBand(ItemID.KEYHALF2, 1006, 1006, 1008),
		new BarrowsBand(ItemID.KEYHALF1, 1009, 1009, 1011),
		new BarrowsBand(ItemID.DRAGON_MED_HELM, 1012, 1012, 1012),
	};

	private static final Map<LootSource, Table> TABLES = loadTables();

	private ClueLootSimulator() {}

	static List<Loot> generate(int casketItemId)
	{
		return generate(sourceForCasket(casketItemId), null);
	}

	static List<Loot> generate(int casketItemId, LootRarity requestedRarity)
	{
		return generate(sourceForCasket(casketItemId), requestedRarity);
	}

	static List<Loot> generate(LootSource source)
	{
		return generate(source, null);
	}

	static List<Loot> generate(LootSource source, LootRarity requestedRarity)
	{
		return generate(source, requestedRarity, RunContext.FULL);
	}

	/**
	 * Generates a teaser reward as the given run could have produced it. A partial run
	 * (fewer Barrows brothers or Moons) may be unable to reach the requested rarity; the
	 * closest attempt is returned then, since an impossible reel tile would be misleading.
	 */
	static List<Loot> generate(LootSource source, LootRarity requestedRarity, RunContext context)
	{
		Table table = tableFor(source);
		RunContext run = context == null ? RunContext.FULL : context;
		List<Loot> loot = null;
		for (int attempt = 0; attempt < MAX_TARGET_GENERATION_ATTEMPTS; attempt++)
		{
			loot = table.generateCandidate(requestedRarity, ThreadLocalRandom.current(), run);
			if (requestedRarity == null || table.rarityFor(loot) == requestedRarity)
			{
				return loot;
			}
		}
		if (run != RunContext.FULL)
		{
			return loot;
		}
		throw new IllegalStateException("Unable to generate " + requestedRarity
			+ " teaser contents for " + source + " after " + MAX_TARGET_GENERATION_ATTEMPTS + " attempts");
	}

	static LootRarity rarityFor(List<Loot> loot, int casketItemId)
	{
		return rarityFor(loot, sourceForCasket(casketItemId));
	}

	static LootRarity rarityFor(List<Loot> loot, LootSource source)
	{
		return tableFor(source).rarityFor(loot);
	}

	static long[] valueThresholdsFor(int casketItemId)
	{
		return valueThresholdsFor(sourceForCasket(casketItemId));
	}

	static long[] valueThresholdsFor(LootSource source)
	{
		long[] thresholds = tableFor(source).valueThresholds;
		return Arrays.copyOf(thresholds, thresholds.length);
	}

	static boolean hasTable(LootSource source)
	{
		Table table = TABLES.get(source);
		return table != null && !table.entries.isEmpty();
	}

	private static LootSource sourceForCasket(int casketItemId)
	{
		LootSource source = LootSource.forCasket(casketItemId);
		return source == null ? LootSource.HARD_CLUE : source;
	}

	private static Table tableFor(LootSource source)
	{
		Table table = TABLES.get(source);
		if (table == null || table.entries.isEmpty())
		{
			throw new IllegalStateException("No loot data loaded for " + source);
		}
		return table;
	}

	private static Map<LootSource, Table> loadTables()
	{
		Map<LootSource, Table> tables = new EnumMap<>(LootSource.class);
		for (String resource : RESOURCES)
		{
			try (InputStream stream = ClueLootSimulator.class.getResourceAsStream(resource))
			{
				if (stream == null) { throw new IllegalStateException("Missing resource " + resource); }
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
				{
					reader.readLine();
					String line;
					while ((line = reader.readLine()) != null)
					{
						List<String> fields = parseCsvLine(line);
						if (fields.size() < 8) { continue; }
						LootSource source = LootSource.forKey(fields.get(0));
						if (source == null) { continue; }
						Entry entry = new Entry(fields.get(1), Integer.parseInt(fields.get(2)),
							Integer.parseInt(fields.get(4)), Integer.parseInt(fields.get(5)),
							Double.parseDouble(fields.get(6)), Long.parseLong(fields.get(7)));
						tables.computeIfAbsent(source, Table::new).add(entry);
					}
				}
			}
			catch (IOException | NumberFormatException exception)
			{
				throw new IllegalStateException("Unable to load " + resource, exception);
			}
		}
		for (Table table : tables.values())
		{
			table.buildRarityBands();
			table.buildValueThresholds(table.source.getKey().hashCode());
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

	private static final class Table
	{
		private final LootSource source;
		private final List<Entry> entries = new ArrayList<>();
		private final List<Entry> uniqueEntries = new ArrayList<>();
		private final List<Entry> commonEntries = new ArrayList<>();
		private final Map<Integer, Double> probabilityByItem = new HashMap<>();
		private final Map<Integer, String> sectionByItem = new HashMap<>();
		private final Map<Integer, Long> gePriceByItem = new HashMap<>();
		private final Map<Integer, LootRarity> rarityByItem = new HashMap<>();
		private final long[] valueThresholds = new long[4];

		private Table(LootSource source)
		{
			this.source = source;
		}

		private void add(Entry entry)
		{
			entries.add(entry);
			if (isUniqueSection(entry.section))
			{
				uniqueEntries.add(entry);
			}
			else
			{
				commonEntries.add(entry);
			}
			probabilityByItem.merge(entry.itemId, entry.weight, Double::sum);
			sectionByItem.putIfAbsent(entry.itemId, entry.section);
			gePriceByItem.put(entry.itemId, entry.gePrice);
		}

		/** Builds one plausible reward. A requested rarity guarantees one item from that band. */
		private List<Loot> generateCandidate(LootRarity requestedRarity, Random random, RunContext run)
		{
			if (source == LootSource.BARROWS)
			{
				return barrowsCandidate(requestedRarity, random, run);
			}
			if (source == LootSource.LUNAR_CHEST)
			{
				return lunarCandidate(requestedRarity, random, run);
			}
			int rolls = source.getMinimumRolls()
				+ random.nextInt(source.getMaximumRolls() - source.getMinimumRolls() + 1);
			Map<Integer, Integer> combined = new LinkedHashMap<>();
			LootRarity selected = requestedRarity == null ? null : requireAvailableRarity(requestedRarity);
			LootSource.UniqueMode mode = source.getUniqueMode();
			if (mode == LootSource.UniqueMode.ROLLED || uniqueEntries.isEmpty() || commonEntries.isEmpty())
			{
				for (int roll = 0; roll < rolls; roll++)
				{
					// Other rolls may be more common, but never rarer, so the bundle
					// retains exactly the requested rarity.
					Entry entry = selected == null
						? pick(entries, random)
						: pickForRarity(entries, selected, roll == 0, random);
					addRoll(combined, entry, random);
				}
				return toLoot(combined);
			}

			int commonRolls = rolls;
			if (selected != null)
			{
				Entry guaranteed = pickForRarity(entries, selected, true, random);
				addRoll(combined, guaranteed, random);
				boolean unique = uniqueEntries.contains(guaranteed);
				if (unique && mode == LootSource.UniqueMode.EXCLUSIVE)
				{
					return toLoot(combined);
				}
				if (!unique)
				{
					commonRolls--;
				}
			}
			else if (random.nextDouble() < source.getExclusiveUniqueChance())
			{
				addRoll(combined, pick(uniqueEntries, random), random);
				return toLoot(combined);
			}

			for (int roll = 0; roll < commonRolls; roll++)
			{
				Entry entry = selected == null
					? pick(commonEntries, random)
					: pickForRarity(commonEntries, selected, false, random);
				addRoll(combined, entry, random);
			}
			return toLoot(combined);
		}

		// ------------------------------------------------------------ Barrows

		/**
		 * Barrows: 1 + brothers killed rolls. Each roll first tries 1/(450 - 58N) for a piece
		 * from a killed brother (never the same piece twice in one chest); otherwise a value in
		 * 1..reward potential picks the common item, whose quantity grows with the potential.
		 */
		private List<Loot> barrowsCandidate(LootRarity requested, Random random, RunContext run)
		{
			List<Entry> remainingUniques = new ArrayList<>();
			for (int index = 0; index < RunContext.BROTHERS.length; index++)
			{
				if (run.killedBrother(index))
				{
					remainingUniques.addAll(entriesInSection(RunContext.BROTHERS[index] + " uniques"));
				}
			}
			int brothers = run.brothersKilled();
			int potential = run.rewardPotential();
			int rolls = 1 + brothers;
			Map<Integer, Integer> combined = new LinkedHashMap<>();
			int roll = 0;

			if (requested != null)
			{
				List<Entry> exact = new ArrayList<>();
				for (Entry unique : remainingUniques)
				{
					if (rarityOf(unique) == requested) { exact.add(unique); }
				}
				for (BarrowsBand band : BARROWS_BANDS)
				{
					Entry common = commonEntry(band.itemId);
					if (band.unlock <= potential && common != null && rarityOf(common) == requested)
					{
						exact.add(common);
					}
				}
				if (!exact.isEmpty())
				{
					Entry chosen = pick(exact, random);
					if (remainingUniques.remove(chosen)) { addRoll(combined, chosen, random); }
					else { addBarrowsCommon(combined, bandFor(chosen.itemId), potential, random); }
					roll = 1;
				}
			}

			for (; roll < rolls; roll++)
			{
				if (brothers > 0 && !remainingUniques.isEmpty()
					&& random.nextDouble() < 1.0 / (450 - 58 * brothers))
				{
					Entry unique = remainingUniques.remove(random.nextInt(remainingUniques.size()));
					if (requested == null || rarityOf(unique).ordinal() <= requested.ordinal())
					{
						addRoll(combined, unique, random);
						continue;
					}
				}
				BarrowsBand band = bandForPotentialRoll(1 + random.nextInt(potential));
				// Keep a requested rarity exact: re-draw commons that would outrank it.
				for (int redraw = 0; requested != null && redraw < 10
					&& rarityOf(commonEntry(band.itemId)).ordinal() > requested.ordinal(); redraw++)
				{
					band = bandForPotentialRoll(1 + random.nextInt(potential));
				}
				addBarrowsCommon(combined, band, potential, random);
			}
			return toLoot(combined);
		}

		private void addBarrowsCommon(Map<Integer, Integer> combined, BarrowsBand band, int potential,
			Random random)
		{
			Entry entry = commonEntry(band.itemId);
			int minimum = entry == null ? 1 : entry.minimumQuantity;
			int maximum = entry == null ? 1 : entry.maximumQuantity;
			double scale = band.fullQuantity <= band.unlock ? 1.0
				: Math.max(0.0, Math.min(1.0, (potential - band.unlock) / (double) (band.fullQuantity - band.unlock)));
			int scaledMaximum = minimum + (int) Math.round((maximum - minimum) * scale);
			combined.merge(band.itemId, minimum + random.nextInt(scaledMaximum - minimum + 1), Integer::sum);
		}

		private Entry commonEntry(int itemId)
		{
			for (Entry entry : commonEntries)
			{
				if (entry.itemId == itemId) { return entry; }
			}
			return null;
		}

		private List<Entry> entriesInSection(String section)
		{
			List<Entry> matching = new ArrayList<>();
			for (Entry entry : entries)
			{
				if (entry.section.equalsIgnoreCase(section)) { matching.add(entry); }
			}
			return matching;
		}

		private static BarrowsBand bandFor(int itemId)
		{
			for (BarrowsBand band : BARROWS_BANDS)
			{
				if (band.itemId == itemId) { return band; }
			}
			return BARROWS_BANDS[0];
		}

		private static BarrowsBand bandForPotentialRoll(int value)
		{
			for (BarrowsBand band : BARROWS_BANDS)
			{
				if (value <= band.last) { return band; }
			}
			return BARROWS_BANDS[BARROWS_BANDS.length - 1];
		}

		// ------------------------------------------------------------ Lunar Chest

		/**
		 * Lunar Chest: each defeated Moon independently has a 1/56 chance at a piece of its own
		 * set. Any unique replaces the standard loot; otherwise the standard table is rolled
		 * 1, 3 or 6 times for 1, 2 or 3 Moons.
		 */
		private List<Loot> lunarCandidate(LootRarity requested, Random random, RunContext run)
		{
			List<List<Entry>> sets = new ArrayList<>();
			for (int index = 0; index < RunContext.MOONS.length; index++)
			{
				if (run.defeatedMoon(index))
				{
					sets.add(entriesInSection(RunContext.MOONS[index] + " uniques"));
				}
			}
			int rolls = LUNAR_ROLLS[Math.min(3, Math.max(1, sets.size()))];
			Map<Integer, Integer> combined = new LinkedHashMap<>();
			int remainingRolls = rolls;

			if (requested != null)
			{
				List<Entry> exact = new ArrayList<>();
				for (List<Entry> set : sets)
				{
					for (Entry unique : set)
					{
						if (rarityOf(unique) == requested) { exact.add(unique); }
					}
				}
				for (Entry common : commonEntries)
				{
					if (rarityOf(common) == requested) { exact.add(common); }
				}
				if (!exact.isEmpty())
				{
					Entry chosen = pick(exact, random);
					addRoll(combined, chosen, random);
					if (uniqueEntries.contains(chosen))
					{
						return toLoot(combined);
					}
					remainingRolls--;
				}
			}
			else
			{
				for (List<Entry> set : sets)
				{
					if (!set.isEmpty() && random.nextDouble() < LUNAR_UNIQUE_CHANCE)
					{
						addRoll(combined, set.get(random.nextInt(set.size())), random);
					}
				}
				if (!combined.isEmpty())
				{
					return toLoot(combined);
				}
			}

			for (int roll = 0; roll < remainingRolls; roll++)
			{
				Entry entry = requested == null
					? pick(commonEntries, random)
					: pickForRarity(commonEntries, requested, false, random);
				addRoll(combined, entry, random);
			}
			return toLoot(combined);
		}

		private static void addRoll(Map<Integer, Integer> combined, Entry entry, Random random)
		{
			int quantity = entry.minimumQuantity
				+ random.nextInt(entry.maximumQuantity - entry.minimumQuantity + 1);
			combined.merge(entry.itemId, quantity, Integer::sum);
		}

		private static List<Loot> toLoot(Map<Integer, Integer> combined)
		{
			List<Loot> loot = new ArrayList<>();
			combined.forEach((itemId, quantity) -> loot.add(new Loot(itemId, quantity)));
			return loot;
		}

		private static Entry pick(List<Entry> pool, Random random)
		{
			double total = 0.0;
			for (Entry entry : pool) { total += entry.weight; }
			double target = random.nextDouble() * total;
			double cumulative = 0.0;
			for (Entry entry : pool)
			{
				cumulative += entry.weight;
				if (target < cumulative) { return entry; }
			}
			return pool.get(pool.size() - 1);
		}

		private LootRarity rarityFor(List<Loot> loot)
		{
			LootRarity rarest = LootRarity.MIL_SPEC;
			long totalGeValue = 0L;
			for (Loot reward : loot)
			{
				LootRarity itemRarity = rarityByItem.getOrDefault(reward.getItemId(), LootRarity.MIL_SPEC);
				if (itemRarity.ordinal() > rarest.ordinal())
				{
					rarest = itemRarity;
				}
				long unitPrice = gePriceByItem.getOrDefault(reward.getItemId(), 0L);
				try
				{
					long itemValue = Math.multiplyExact(unitPrice, (long) reward.getQuantity());
					totalGeValue = Math.addExact(totalGeValue, itemValue);
				}
				catch (ArithmeticException ignored)
				{
					totalGeValue = Long.MAX_VALUE;
				}
			}
			LootRarity valueFloor = rarityForTotalValue(totalGeValue);
			return valueFloor.ordinal() > rarest.ordinal() ? valueFloor : rarest;
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
					rarityByItem.put(item.getKey(), LootRarity.RARE_SPECIAL);
					continue;
				}
				List<Integer> items = isUniqueSection(section)
					? uniqueItemsBySection.computeIfAbsent(section, ignored -> new ArrayList<>())
					: ordinaryItems;
				items.add(item.getKey());
			}
			assignRelativeRarities(ordinaryItems, false);
			for (List<Integer> items : uniqueItemsBySection.values())
			{
				assignRelativeRarities(items, true);
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
				double probabilityRank = normalizedRank(probabilityLevels, probabilityByItem.get(itemId));
				double priceRank = normalizedRank(priceLevels, gePriceByItem.getOrDefault(itemId, 0L));
				double score = probabilityRank * TABLE_RARITY_WEIGHT + priceRank * GE_VALUE_WEIGHT;
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
			int band = Math.min(bands.length - 1, (int) Math.floor(relativeRank * bands.length));
			return bands[band];
		}

		/** Clue "X clue uniques" sections and chest sections tagged "... uniques". */
		private static boolean isUniqueSection(String section)
		{
			return section != null && section.toLowerCase().endsWith("uniques");
		}

		private static boolean isMegaRare(String section)
		{
			return section != null && section.toLowerCase().contains("mega");
		}

		private void buildValueThresholds(int seed)
		{
			final int samples = 100_000;
			long[] values = new long[samples];
			Random random = new Random(0x43415345L ^ seed);
			for (int sample = 0; sample < samples; sample++)
			{
				long total = 0L;
				for (Loot reward : generateCandidate(null, random, RunContext.FULL))
				{
					total += gePriceByItem.getOrDefault(reward.getItemId(), 0L) * (long) reward.getQuantity();
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
			throw new IllegalStateException("No " + requested + " rewards in " + source + " table");
		}

		private Entry pickForRarity(List<Entry> pool, LootRarity rarity, boolean exact, Random random)
		{
			double eligibleWeight = 0.0;
			for (Entry entry : pool)
			{
				if (eligible(entry, rarity, exact)) { eligibleWeight += entry.weight; }
			}
			if (eligibleWeight <= 0.0)
			{
				// No commons at or below this band: fall back to the cheapest band available.
				return pickForRarity(pool, LootRarity.RARE_SPECIAL, false, random);
			}

			double target = random.nextDouble() * eligibleWeight;
			double cumulative = 0.0;
			Entry fallback = null;
			for (Entry entry : pool)
			{
				if (eligible(entry, rarity, exact))
				{
					fallback = entry;
					cumulative += entry.weight;
					if (target < cumulative) { return entry; }
				}
			}
			return fallback;
		}

		private boolean eligible(Entry entry, LootRarity rarity, boolean exact)
		{
			LootRarity entryRarity = rarityOf(entry);
			return exact ? entryRarity == rarity : entryRarity.ordinal() <= rarity.ordinal();
		}
	}

	private static final class BarrowsBand
	{
		private final int itemId; private final int unlock; private final int fullQuantity; private final int last;
		private BarrowsBand(int itemId, int unlock, int fullQuantity, int last)
		{
			this.itemId = itemId; this.unlock = unlock; this.fullQuantity = fullQuantity; this.last = last;
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
