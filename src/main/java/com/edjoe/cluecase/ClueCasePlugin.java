package com.edjoe.cluecase;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Named;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Clue Case Opening",
	description = "Shows a CS-style reward reel after you open a clue casket.",
	tags = {"clue", "casket", "loot", "rewards", "animation"}
)
@Slf4j
public class ClueCasePlugin extends Plugin
{
	private static final long OPEN_RESULT_TIMEOUT_MILLIS = 5_000L;
	private static final Pattern CLUE_COMPLETED_PATTERN = Pattern.compile(
		"You have completed [0-9]+ (beginner|easy|medium|hard|elite|master) Treasure Trails?\\."
	);

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private ClueCaseConfig config;

	@Inject
	@Named("developerMode")
	private boolean developerMode;

	@Inject
	private ClueCaseOverlay overlay;

	private Map<Integer, Integer> inventoryBeforeOpen;
	private long casketOpenedAt;
	private int openedCasketItemId;

	@Provides
	ClueCaseConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ClueCaseConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		keyManager.registerKeyListener(overlay);
		mouseManager.registerMouseListener(overlay);
		log.debug("Clue Case Opening started");
	}

	@Override
	protected void shutDown()
	{
		log.debug("Clue Case Opening shutting down");
		overlayManager.remove(overlay);
		keyManager.unregisterKeyListener(overlay);
		mouseManager.unregisterMouseListener(overlay);
		inventoryBeforeOpen = null;
		overlay.clear();
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!event.getMenuOption().toLowerCase(Locale.ROOT).startsWith("open"))
		{
			return;
		}

		boolean clueCasket = isClueCasket(event.getItemId());
		log.debug("Open click received: itemId={}, clueCasket={}", event.getItemId(), clueCasket);
		if (!clueCasket)
		{
			return;
		}
		if (!config.animateClueTiers().includes(clueTier(event.getItemId())))
		{
			log.debug("Skipping animation for excluded clue tier: itemId={}", event.getItemId());
			return;
		}

		inventoryBeforeOpen = inventoryQuantities(client.getItemContainer(InventoryID.INVENTORY));
		casketOpenedAt = System.currentTimeMillis();
		openedCasketItemId = event.getItemId();
		overlay.begin(openedCasketItemId);
		log.debug("Captured inventory before casket open: {} item types", inventoryBeforeOpen.size());
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}

		String message = Text.removeTags(event.getMessage());
		if (!CLUE_COMPLETED_PATTERN.matcher(message).matches())
		{
			return;
		}

		inventoryBeforeOpen = inventoryQuantities(client.getItemContainer(InventoryID.INVENTORY));
		casketOpenedAt = System.currentTimeMillis();
		log.debug("Clue completion message received; refreshed reward inventory baseline with {} item types",
			inventoryBeforeOpen.size());
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != InterfaceID.TRAIL_REWARDSCREEN)
		{
			return;
		}

		ItemContainer rewards = client.getItemContainer(net.runelite.api.gameval.InventoryID.TRAIL_REWARDINV);
		List<Loot> loot = lootFromContainer(rewards);
		log.debug("Clue reward screen loaded: {} reward item types", loot.size());
		if (loot.isEmpty())
		{
			log.debug("Reward container was empty when the clue reward screen loaded");
			return;
		}

		inventoryBeforeOpen = null;
		log.debug("Starting reward overlay from clue reward dialog");
		overlay.start(loot, openedCasketItemId);
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		if (!developerMode || !event.getCommand().equalsIgnoreCase("cluecase"))
		{
			return;
		}

		String tier = event.getArguments().length == 0 ? "easy" : event.getArguments()[0].toLowerCase(Locale.ROOT);
		int casketItemId = casketItemIdForTier(tier);
		if (casketItemId == -1)
		{
			log.debug("Unknown clue-case preview tier '{}'; use beginner, easy, medium, hard, elite, or master", tier);
			return;
		}

		List<Loot> simulatedLoot = ClueLootSimulator.generate(casketItemId);
		log.debug("Starting developer preview for {} clue with {} reward item types", tier, simulatedLoot.size());
		overlay.start(simulatedLoot, casketItemId);
	}

	private static int casketItemIdForTier(String tier)
	{
		switch (tier)
		{
			case "beginner": return ItemID.TRAIL_REWARD_CASKET_BEGINNER;
			case "easy": return ItemID.TRAIL_REWARD_CASKET_EASY;
			case "medium": return ItemID.TRAIL_REWARD_CASKET_MEDIUM;
			case "hard": return ItemID.TRAIL_REWARD_CASKET_HARD;
			case "elite": return ItemID.TRAIL_REWARD_CASKET_ELITE;
			case "master": return ItemID.TRAIL_REWARD_CASKET_MASTER;
			default: return -1;
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INVENTORY.getId() || inventoryBeforeOpen == null)
		{
			return;
		}

		if (System.currentTimeMillis() - casketOpenedAt > OPEN_RESULT_TIMEOUT_MILLIS)
		{
			log.debug("Casket result timed out before an inventory update arrived");
			inventoryBeforeOpen = null;
			return;
		}

		List<Loot> loot = gainedItems(inventoryBeforeOpen, inventoryQuantities(event.getItemContainer()));
		log.debug("Casket inventory update detected: {} gained item types", loot.size());
		if (loot.isEmpty())
		{
			log.debug("No rewards present yet; waiting for the next inventory update");
			return;
		}

		inventoryBeforeOpen = null;
		log.debug("Starting reward overlay");
		overlay.start(loot, openedCasketItemId);
	}

	private boolean isClueCasket(int itemId)
	{
		return itemId == ItemID.TRAIL_REWARD_CASKET_BEGINNER
			|| itemId == ItemID.TRAIL_REWARD_CASKET_EASY
			|| itemId == ItemID.TRAIL_REWARD_CASKET_MEDIUM
			|| itemId == ItemID.TRAIL_REWARD_CASKET_HARD
			|| itemId == ItemID.TRAIL_REWARD_CASKET_ELITE
			|| itemId == ItemID.TRAIL_REWARD_CASKET_MASTER;
	}

	private static int clueTier(int itemId)
	{
		switch (itemId)
		{
			case ItemID.TRAIL_REWARD_CASKET_BEGINNER: return 0;
			case ItemID.TRAIL_REWARD_CASKET_EASY: return 1;
			case ItemID.TRAIL_REWARD_CASKET_MEDIUM: return 2;
			case ItemID.TRAIL_REWARD_CASKET_HARD: return 3;
			case ItemID.TRAIL_REWARD_CASKET_ELITE: return 4;
			case ItemID.TRAIL_REWARD_CASKET_MASTER: return 5;
			default: return -1;
		}
	}

	private static Map<Integer, Integer> inventoryQuantities(ItemContainer container)
	{
		Map<Integer, Integer> quantities = new HashMap<>();
		if (container == null)
		{
			return quantities;
		}

		for (Item item : container.getItems())
		{
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				quantities.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		return quantities;
	}

	private static List<Loot> lootFromContainer(ItemContainer container)
	{
		List<Loot> loot = new ArrayList<>();
		for (Map.Entry<Integer, Integer> entry : inventoryQuantities(container).entrySet())
		{
			loot.add(new Loot(entry.getKey(), entry.getValue()));
		}
		return loot;
	}

	private static List<Loot> gainedItems(Map<Integer, Integer> before, Map<Integer, Integer> after)
	{
		List<Loot> gained = new ArrayList<>();
		for (Map.Entry<Integer, Integer> entry : after.entrySet())
		{
			int gainedQuantity = entry.getValue() - before.getOrDefault(entry.getKey(), 0);
			if (gainedQuantity > 0)
			{
				gained.add(new Loot(entry.getKey(), gainedQuantity));
			}
		}
		return gained;
	}
}
