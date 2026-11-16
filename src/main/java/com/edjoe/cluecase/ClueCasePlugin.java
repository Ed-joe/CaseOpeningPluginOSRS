package com.edjoe.cluecase;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Named;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
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
	description = "Shows a CS-style reward reel after you open a clue casket or reward chest.",
	tags = {"clue", "casket", "loot", "rewards", "animation", "barrows", "lunar", "raids", "chest"}
)
@Slf4j
public class ClueCasePlugin extends Plugin
{
	private static final long OPEN_RESULT_TIMEOUT_MILLIS = 5_000L;
	/** Brother KILLED varbits, in {@link RunContext#BROTHERS} order. */
	private static final int[] BROTHER_VARBITS = {
		VarbitID.BARROWS_KILLED_AHRIM, VarbitID.BARROWS_KILLED_DHAROK, VarbitID.BARROWS_KILLED_GUTHAN,
		VarbitID.BARROWS_KILLED_KARIL, VarbitID.BARROWS_KILLED_TORAG, VarbitID.BARROWS_KILLED_VERAC
	};
	/** Moon kill-count varps, in {@link RunContext#MOONS} order. */
	private static final int[] MOON_KILL_VARPS = {
		VarPlayerID.PMOON_BLOOD_KILLS, VarPlayerID.PMOON_BLUE_KILLS, VarPlayerID.PMOON_ECLIPSE_KILLS
	};
	private static final String PENDING_MOONS_KEY = "pendingLunarMoons";
	/** Moon defeated varbits, in {@link RunContext#MOONS} order. */
	private static final int[] MOON_VARBITS = {
		VarbitID.PMOON_BOSS_BLOOD_DEAD, VarbitID.PMOON_BOSS_BLUE_DEAD, VarbitID.PMOON_BOSS_ECLIPSE_DEAD
	};

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
	private ConfigManager configManager;

	@Inject
	@Named("developerMode")
	private boolean developerMode;

	@Inject
	private ClueCaseOverlay overlay;

	private long casketOpenedAt;
	private LootSource pendingSource;
	private RunContext pendingRun = RunContext.FULL;
	// Last non-empty progress seen during the current run, in case the game clears the
	// varbits as the chest opens. Cleared once a chest reveal has used it.
	private RunContext lastBarrowsRun;
	/** Last Moon kill counts seen this login, in {@link RunContext#MOONS} order; -1 = not seen yet. */
	private final int[] lastMoonKills = {-1, -1, -1};
	private String lastChestSignature;
	private boolean pendingCasketOpen;
	private boolean rewardScreenLoaded;
	private final List<Widget> hiddenRewardWidgets = new ArrayList<>();
	private Widget hiddenNotificationWidget;
	private Widget hiddenChatbox;

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
		restoreChatbox();
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!event.getMenuOption().toLowerCase(Locale.ROOT).startsWith("open"))
		{
			return;
		}

		LootSource source = LootSource.forCasket(event.getItemId());
		log.debug("Open click received: itemId={}, clueCasket={}", event.getItemId(), source != null);
		if (source == null)
		{
			return;
		}
		if (!config.animateClueTiers().includes(source.clueTier()))
		{
			log.debug("Skipping animation for excluded clue tier: itemId={}", event.getItemId());
			return;
		}

		casketOpenedAt = System.currentTimeMillis();
		pendingSource = source;
		pendingCasketOpen = true;
		rewardScreenLoaded = false;
		log.debug("Awaiting reward screen for {}", pendingSource);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.NOTIFICATION_DISPLAY && overlay.isActive())
		{
			hideNotificationWidget();
			return;
		}
		LootSource chest = LootSource.forInterface(event.getGroupId());
		if (chest != null)
		{
			onChestRewardLoaded(chest);
			return;
		}
		if (event.getGroupId() != InterfaceID.TRAIL_REWARDSCREEN || !pendingCasketOpen
			|| pendingSource == null || !pendingSource.isClue())
		{
			return;
		}
		hideRewardWidget();
		rewardScreenLoaded = true;
		overlay.begin(pendingSource);
		updateChatboxVisibility();
		log.debug("Reward screen confirmed for {}", pendingSource);
		tryStartFromRewardContainer();
	}

	private void onChestRewardLoaded(LootSource chest)
	{
		// Read (and consume) the run first, so a skipped animation can't leave this
		// chest's Moons or brothers counted toward the next one.
		RunContext run = runFor(chest);
		if (!isChestEnabled(chest) || overlay.isActive())
		{
			return;
		}
		// Raid reward interfaces can be reopened by clicking the chest again; only
		// animate a given haul once.
		List<Loot> alreadyLoaded = lootFromContainer(client.getItemContainer(chest.getInventoryId()));
		if (!alreadyLoaded.isEmpty() && signature(chest, alreadyLoaded).equals(lastChestSignature))
		{
			log.debug("Skipping repeat view of {} loot", chest);
			return;
		}

		casketOpenedAt = System.currentTimeMillis();
		pendingSource = chest;
		pendingRun = run;
		pendingCasketOpen = true;
		rewardScreenLoaded = true;
		hideInterfaceGroup(chest.getInterfaceGroupId());
		overlay.begin(chest);
		updateChatboxVisibility();
		log.debug("Reward interface loaded for {} ({})", chest, pendingRun);
		tryStartFromRewardContainer();
	}

	private boolean isChestEnabled(LootSource chest)
	{
		switch (chest)
		{
			case BARROWS: return config.animateBarrows();
			case LUNAR_CHEST: return config.animateLunarChest();
			case CHAMBERS_OF_XERIC:
			case THEATRE_OF_BLOOD:
			case TOMBS_OF_AMASCUT: return config.animateRaids();
			default: return false;
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		updateChatboxVisibility();
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

		String key = event.getArguments().length == 0 ? "easy" : event.getArguments()[0].toLowerCase(Locale.ROOT);
		LootSource source = LootSource.forKey(key);
		if (source == null)
		{
			StringBuilder keys = new StringBuilder();
			for (LootSource candidate : LootSource.values())
			{
				keys.append(keys.length() == 0 ? "" : ", ").append(candidate.getKey());
			}
			log.debug("Unknown clue-case preview source '{}'; use one of {}", key, keys);
			return;
		}

		RunContext run = previewRun(source, event.getArguments());
		List<Loot> simulatedLoot = ClueLootSimulator.generate(source, null, run);
		log.debug("Starting developer preview for {} ({}) with {} reward item types", source, run,
			simulatedLoot.size());
		overlay.start(simulatedLoot, source, run);
	}

	/**
	 * ::cluecase barrows [brothers 0-6] [monster combat sum 0-1000]
	 * ::cluecase lunar [moons 1-3]
	 * Missing arguments mean a full run.
	 */
	private static RunContext previewRun(LootSource source, String[] arguments)
	{
		try
		{
			if (source == LootSource.BARROWS && arguments.length > 1)
			{
				int brothers = Math.max(0, Math.min(6, Integer.parseInt(arguments[1])));
				int combatSum = arguments.length > 2 ? Integer.parseInt(arguments[2]) : 1000;
				return RunContext.barrows((1 << brothers) - 1, combatSum);
			}
			if (source == LootSource.LUNAR_CHEST && arguments.length > 1)
			{
				int moons = Math.max(1, Math.min(3, Integer.parseInt(arguments[1])));
				return RunContext.lunar((1 << moons) - 1);
			}
		}
		catch (NumberFormatException ignored)
		{
			// Fall through to a full run.
		}
		return RunContext.FULL;
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		int varbitId = event.getVarbitId();
		if (varbitId == VarbitID.BARROWS_KILLED_MONSTER || contains(BROTHER_VARBITS, varbitId))
		{
			RunContext run = readBarrowsRun();
			if (run != null)
			{
				lastBarrowsRun = run;
			}
		}
		else if (contains(MOON_VARBITS, varbitId))
		{
			// A Moon's "dead" flag clears once you leave its arena, so add to the
			// pending mask rather than replacing it.
			addPendingMoons(currentlyDeadMoons());
		}

		int moon = indexOf(MOON_KILL_VARPS, event.getVarpId());
		if (moon >= 0)
		{
			int kills = client.getVarpValue(MOON_KILL_VARPS[moon]);
			// The first value seen after login is the existing count, not a new kill.
			if (lastMoonKills[moon] >= 0 && kills > lastMoonKills[moon])
			{
				addPendingMoons(1 << moon);
			}
			lastMoonKills[moon] = kills;
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			Arrays.fill(lastMoonKills, -1);
		}
	}

	/**
	 * Moons defeated since the last Lunar Chest, kept per account so kills before a
	 * client restart still count. The chest holds loot from every Moon killed until looted.
	 */
	private int pendingMoons()
	{
		Integer mask = configManager.getRSProfileConfiguration(ClueCaseConfig.GROUP, PENDING_MOONS_KEY, Integer.class);
		return mask == null ? 0 : mask;
	}

	private void addPendingMoons(int moons)
	{
		int current = pendingMoons();
		int updated = current | (moons & RunContext.ALL_MOONS);
		if (updated != current)
		{
			configManager.setRSProfileConfiguration(ClueCaseConfig.GROUP, PENDING_MOONS_KEY, updated);
			log.debug("Pending Lunar Chest moons now {}", Integer.toBinaryString(updated));
		}
	}

	private int currentlyDeadMoons()
	{
		int moons = 0;
		for (int index = 0; index < MOON_VARBITS.length; index++)
		{
			if (client.getVarbitValue(MOON_VARBITS[index]) > 0)
			{
				moons |= 1 << index;
			}
		}
		return moons;
	}

	private static int indexOf(int[] values, int value)
	{
		for (int index = 0; index < values.length; index++)
		{
			if (values[index] == value) { return index; }
		}
		return -1;
	}

	/** The run behind a chest: live varbits, else the last progress seen, else a full run. */
	private RunContext runFor(LootSource chest)
	{
		RunContext run = null;
		if (chest == LootSource.BARROWS)
		{
			run = readBarrowsRun();
			if (run == null) { run = lastBarrowsRun; }
			lastBarrowsRun = null;
		}
		else if (chest == LootSource.LUNAR_CHEST)
		{
			int moons = pendingMoons() | currentlyDeadMoons();
			configManager.unsetRSProfileConfiguration(ClueCaseConfig.GROUP, PENDING_MOONS_KEY);
			run = moons == 0 ? null : RunContext.lunar(moons);
		}
		return run == null ? RunContext.FULL : run;
	}

	/** Current Barrows progress, or null when nothing has been killed this run. */
	private RunContext readBarrowsRun()
	{
		int brothers = 0;
		for (int index = 0; index < BROTHER_VARBITS.length; index++)
		{
			if (client.getVarbitValue(BROTHER_VARBITS[index]) > 0)
			{
				brothers |= 1 << index;
			}
		}
		int combatSum = client.getVarbitValue(VarbitID.BARROWS_KILLED_MONSTER);
		return brothers == 0 && combatSum == 0 ? null : RunContext.barrows(brothers, combatSum);
	}

	private static boolean contains(int[] values, int value)
	{
		for (int candidate : values)
		{
			if (candidate == value) { return true; }
		}
		return false;
	}

	private void tryStartFromRewardContainer()
	{
		LootSource source = pendingSource;
		RunContext run = pendingRun;
		if (source == null)
		{
			return;
		}
		int inventoryId = source.isClue()
			? net.runelite.api.gameval.InventoryID.TRAIL_REWARDINV
			: source.getInventoryId();
		List<Loot> loot = lootFromContainer(client.getItemContainer(inventoryId));
		if (loot.isEmpty())
		{
			return;
		}
		clearPendingOpen();
		if (!source.isClue())
		{
			String chestSignature = signature(source, loot);
			if (chestSignature.equals(lastChestSignature))
			{
				log.debug("Skipping repeat view of {} loot", source);
				overlay.clear();
				return;
			}
			lastChestSignature = chestSignature;
		}
		log.debug("Starting reward overlay from {} reward interface", source);
		overlay.start(loot, source, run);
	}

	private static String signature(LootSource source, List<Loot> loot)
	{
		StringBuilder builder = new StringBuilder(source.name());
		for (Loot reward : loot)
		{
			builder.append(';').append(reward.getItemId()).append('x').append(reward.getQuantity());
		}
		return builder.toString();
	}

	private void clearPendingOpen()
	{
		pendingCasketOpen = false;
		rewardScreenLoaded = false;
		pendingSource = null;
		pendingRun = RunContext.FULL;
		casketOpenedAt = 0L;
	}

	private void hideRewardWidget()
	{
		hiddenRewardWidgets.clear();
		for (int childId = 0; childId <= 4; childId++)
		{
			Widget widget = client.getWidget(InterfaceID.TRAIL_REWARDSCREEN, childId);
			if (widget != null && !widget.isHidden())
			{
				widget.setHidden(true);
				hiddenRewardWidgets.add(widget);
			}
		}
	}

	/** Hides an interface's top-level components, i.e. those parented outside the group. */
	private void hideInterfaceGroup(int groupId)
	{
		hiddenRewardWidgets.clear();
		for (int childId = 0; childId < 512; childId++)
		{
			Widget widget = client.getWidget(groupId, childId);
			if (widget == null)
			{
				break;
			}
			if ((widget.getParentId() >>> 16) != groupId && !widget.isHidden())
			{
				widget.setHidden(true);
				hiddenRewardWidgets.add(widget);
			}
		}
	}

	private void unhideRewardWidget()
	{
		for (Widget widget : hiddenRewardWidgets)
		{
			widget.setHidden(false);
		}
		hiddenRewardWidgets.clear();
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

	/**
	 * Keeps the chatbox hidden for the whole reveal so loot messages (the game's own or
	 * other plugins', e.g. loot value announcements) can't spoil it. Re-applied every
	 * client tick because game scripts may re-show the chatbox.
	 */
	private void updateChatboxVisibility()
	{
		if (!overlay.isActive() || !config.hideChatDuringOpening())
		{
			restoreChatbox();
			return;
		}
		Widget chatbox = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
		if (chatbox == null)
		{
			return;
		}
		if (!chatbox.isHidden())
		{
			// Only remember widgets we hid ourselves, so restore never reveals
			// something that was already hidden before the reveal started.
			chatbox.setHidden(true);
			hiddenChatbox = chatbox;
		}
	}

	private void restoreChatbox()
	{
		if (hiddenChatbox != null)
		{
			hiddenChatbox.setHidden(false);
			hiddenChatbox = null;
		}
	}

	private void restoreHiddenWidgets()
	{
		restoreChatbox();
		unhideRewardWidget();
		if (hiddenNotificationWidget != null)
		{
			hiddenNotificationWidget.setHidden(false);
			hiddenNotificationWidget = null;
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
