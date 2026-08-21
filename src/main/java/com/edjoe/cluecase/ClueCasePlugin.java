package com.edjoe.cluecase;

import com.google.inject.Provides;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Named;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Clue Case Opening",
	description = "Shows a CS-style reward reel after you open a clue casket.",
	tags = {"clue", "casket", "loot", "rewards", "animation"}
)
@Slf4j
public class ClueCasePlugin extends Plugin
{
	private static final long OPEN_RESULT_TIMEOUT_MILLIS = 5_000L;

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

	private long casketOpenedAt;
	private int openedCasketItemId;
	private boolean pendingCasketOpen;
	private boolean rewardScreenLoaded;
	private Widget hiddenRewardWidget;
	private Widget hiddenNotificationWidget;

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
		overlay.setOnClose(this::restoreHiddenWidgets);
		log.debug("Clue Case Opening started");
	}

	@Override
	protected void shutDown()
	{
		log.debug("Clue Case Opening shutting down");
		overlayManager.remove(overlay);
		keyManager.unregisterKeyListener(overlay);
		mouseManager.unregisterMouseListener(overlay);
		clearPendingOpen();
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

		casketOpenedAt = System.currentTimeMillis();
		openedCasketItemId = event.getItemId();
		pendingCasketOpen = true;
		rewardScreenLoaded = false;
		overlay.begin(openedCasketItemId);
		log.debug("Authorized reward capture for casket itemId={}", openedCasketItemId);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.NOTIFICATION_DISPLAY && overlay.isActive())
		{
			hideNotificationWidget();
			return;
		}
		if (event.getGroupId() != InterfaceID.TRAIL_REWARDSCREEN || !pendingCasketOpen)
		{
			return;
		}
		hideRewardWidget();
		rewardScreenLoaded = true;
		tryStartFromRewardContainer();
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (!pendingCasketOpen)
		{
			return;
		}
		if (System.currentTimeMillis() - casketOpenedAt > OPEN_RESULT_TIMEOUT_MILLIS)
		{
			log.debug("Casket reward capture timed out");
			clearPendingOpen();
			overlay.clear();
			return;
		}
		if (rewardScreenLoaded)
		{
			tryStartFromRewardContainer();
		}
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

	private void tryStartFromRewardContainer()
	{
		ItemContainer rewards = client.getItemContainer(net.runelite.api.gameval.InventoryID.TRAIL_REWARDINV);
		List<Loot> loot = lootFromContainer(rewards);
		if (loot.isEmpty())
		{
			return;
		}
		log.debug("Starting reward overlay from authorized clue reward dialog");
		int casketItemId = openedCasketItemId;
		clearPendingOpen();
		overlay.start(loot, casketItemId);
	}

	private void clearPendingOpen()
	{
		pendingCasketOpen = false;
		rewardScreenLoaded = false;
		openedCasketItemId = 0;
		casketOpenedAt = 0L;
	}

	private void hideRewardWidget()
	{
		Widget widget = client.getWidget(InterfaceID.TrailRewardscreen.UNIVERSE);
		if (widget == null)
		{
			return;
		}
		widget.setHidden(true);
		hiddenRewardWidget = widget;
	}

	private void unhideRewardWidget()
	{
		if (hiddenRewardWidget != null)
		{
			hiddenRewardWidget.setHidden(false);
			hiddenRewardWidget = null;
		}
	}

	private void hideNotificationWidget()
	{
		Widget widget = client.getWidget(InterfaceID.NotificationDisplay.UNIVERSE);
		if (widget == null)
		{
			return;
		}
		widget.setHidden(true);
		hiddenNotificationWidget = widget;
	}

	private void restoreHiddenWidgets()
	{
		unhideRewardWidget();
		if (hiddenNotificationWidget != null)
		{
			hiddenNotificationWidget.setHidden(false);
			hiddenNotificationWidget = null;
		}
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

	private static List<Loot> lootFromContainer(ItemContainer container)
	{
		List<Loot> loot = new java.util.ArrayList<>();
		if (container == null)
		{
			return loot;
		}
		for (net.runelite.api.Item item : container.getItems())
		{
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				loot.add(new Loot(item.getId(), item.getQuantity()));
			}
		}
		return loot;
	}
}
