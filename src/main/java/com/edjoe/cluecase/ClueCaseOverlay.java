package com.edjoe.cluecase;

import java.awt.Color;
import java.awt.BasicStroke;
import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.imageio.ImageIO;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

@Singleton
class ClueCaseOverlay extends Overlay implements KeyListener, MouseListener
{
	private static final int TILE_SIZE = 132;
	private static final int TILE_GAP = 12;
	private static final int ITEM_ICON_SIZE = 38;
	private static final int PANEL_MARGIN = 12;
	private static final int PANEL_WIDTH = 700;
	private static final int PANEL_HEIGHT = 400;
	private static final int REEL_ITEM_COUNT = 64;
	private static final int WINNING_INDEX = 52;
	private static final int CASKET_DROP_DURATION = 900;
	private static final int CASKET_LANDS_AT = 400;
	private static final int CASKET_OPENING_DURATION = 1_450;
	private static final int CASKET_LID_RELEASE_AT = 950;
	private static final int CASKET_SPRITE_FRAME_WIDTH = 48;
	private static final int CASKET_SPRITE_FRAME_HEIGHT = 48;
	private static final int CASKET_SPRITE_FRAME_COUNT = 6;
	private static final int CASKET_SPRITE_ANIMATION_DURATION = 250;
	private static final int CASKET_SPRITE_SCALE = 8;
	private static final int CASKET_SPRITE_Y_OFFSET = -5;
	private static final int CASKET_SPRITE_VISIBLE_BOTTOM = 43;
	private static final int REEL_DURATION = 4_000;
	private static final BufferedImage HARD_CASKET_OPENING_SHEET = loadCasketOpeningSheet("hard");
	private static final BufferedImage BEGINNER_CASKET_OPENING_SHEET = loadCasketOpeningSheet("beginner");
	private static final BufferedImage EASY_CASKET_OPENING_SHEET = loadCasketOpeningSheet("easy");
	private static final BufferedImage MEDIUM_CASKET_OPENING_SHEET = loadCasketOpeningSheet("medium");
	private static final BufferedImage ELITE_CASKET_OPENING_SHEET = loadCasketOpeningSheet("elite");
	private static final BufferedImage MASTER_CASKET_OPENING_SHEET = loadCasketOpeningSheet("master");

	private final ItemManager itemManager;
	private final ClueCaseConfig config;
	private final ClueCaseSounds sounds;
	private List<List<Loot>> reel = Collections.emptyList();
	private List<LootRarity> reelRarities = Collections.emptyList();
	private List<Loot> actualLoot = Collections.emptyList();
	private int casketItemId;
	private volatile long startedAt;
	private boolean active;
	private boolean awaitingLoot;
	private boolean rewardSoundPlayed;
	private volatile boolean animationComplete;
	private volatile boolean skipToResultRequested;
	private volatile boolean skippedToResult;
	private boolean spaceDown;
	private boolean consumeLeftGesture;
	private boolean dropSoundPlayed;
	private boolean openSoundPlayed;
	private int lastBounceImpact;
	private int lastTickIndex;
	private double winningStopPoint = TILE_SIZE / 2.0;
	private Runnable onClose;

	@Inject
	ClueCaseOverlay(ClueCasePlugin plugin, ItemManager itemManager, ClueCaseConfig config,
		ClueCaseSounds sounds)
	{
		super(plugin);
		this.itemManager = itemManager;
		this.config = config;
		this.sounds = sounds;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	void start(List<Loot> loot, int casketItemId)
	{
		if (!active || !awaitingLoot || this.casketItemId != casketItemId)
		{
			begin(casketItemId);
		}
		actualLoot = Collections.unmodifiableList(new ArrayList<>(loot));
		this.casketItemId = casketItemId;
		reel = buildReel(actualLoot, casketItemId);
		reelRarities = buildReelRarities(actualLoot, casketItemId);
		winningStopPoint = chooseWinningStopPoint();
		awaitingLoot = false;
		if (skipToResultRequested)
		{
			skipToResult();
		}
	}

	void begin(int casketItemId)
	{
		this.casketItemId = casketItemId;
		reel = Collections.emptyList();
		reelRarities = Collections.emptyList();
		actualLoot = Collections.emptyList();
		startedAt = System.currentTimeMillis();
		active = true;
		awaitingLoot = true;
		rewardSoundPlayed = false;
		animationComplete = false;
		skipToResultRequested = false;
		skippedToResult = false;
		spaceDown = false;
		dropSoundPlayed = false;
		openSoundPlayed = false;
		lastBounceImpact = 0;
		lastTickIndex = -1;
	}

	void setOnClose(Runnable onClose)
	{
		this.onClose = onClose;
	}

	boolean isActive()
	{
		return active;
	}

	void clear()
	{
		reel = Collections.emptyList();
		reelRarities = Collections.emptyList();
		actualLoot = Collections.emptyList();
		active = false;
		awaitingLoot = false;
		animationComplete = false;
		skipToResultRequested = false;
		skippedToResult = false;
		spaceDown = false;
		consumeLeftGesture = false;
		if (onClose != null)
		{
			onClose.run();
		}
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!active)
		{
			return null;
		}

		long elapsed = System.currentTimeMillis() - startedAt;
		int duration = REEL_DURATION;
		Rectangle clip = graphics.getClipBounds();
		int panelWidth = Math.min(PANEL_WIDTH, Math.max(TILE_SIZE + PANEL_MARGIN * 2, clip.width - 32));
		int panelHeight = Math.min(PANEL_HEIGHT, clip.height);
		int x = clip.x + (clip.width - panelWidth) / 2;
		int y = clip.y + (clip.height - panelHeight) / 2;
		int reelX = x + PANEL_MARGIN;
		int reelWidth = panelWidth - PANEL_MARGIN * 2;
		int reelY = y + (panelHeight - TILE_SIZE) / 2;
		drawFrame(graphics, x, y, panelWidth, panelHeight, reelY);
		if (elapsed < CASKET_DROP_DURATION)
		{
			if (!skippedToResult && elapsed >= CASKET_LANDS_AT && !dropSoundPlayed)
			{
				dropSoundPlayed = true;
				sounds.playDrop();
			}
			if (elapsed >= CASKET_LANDS_AT)
			{
				double impactProgress = Math.min(1.0, (elapsed - CASKET_LANDS_AT)
					/ (double) (CASKET_DROP_DURATION - CASKET_LANDS_AT));
				int bounceImpact = Math.min(2, (int) (impactProgress * 3.0));
				if (!skippedToResult && bounceImpact > lastBounceImpact)
				{
					lastBounceImpact = bounceImpact;
					sounds.playBounce();
				}
			}
			drawCasketDrop(graphics, x, y, panelWidth, panelHeight, elapsed);
			return null;
		}

		if (!skippedToResult && !openSoundPlayed)
		{
			openSoundPlayed = true;
			sounds.playOpen();
		}
		if (elapsed < CASKET_DROP_DURATION + CASKET_OPENING_DURATION)
		{
			drawCasketOpening(graphics, x, y, panelWidth, panelHeight, elapsed - CASKET_DROP_DURATION);
			return null;
		}

		elapsed -= CASKET_DROP_DURATION + CASKET_OPENING_DURATION;
		if (reel.isEmpty())
		{
			return null;
		}

		double progress = Math.min(1.0, elapsed / (double) duration);
		// Cubic ease-out: quick spin at first, then a satisfying slow-down.
		double eased = 1.0 - Math.pow(1.0 - progress, 3.0);
		double stopOffset = WINNING_INDEX * (TILE_SIZE + TILE_GAP)
			+ winningStopPoint - reelWidth / 2.0;
		double offset = eased * stopOffset;
		int tickIndex = (int) (offset / (TILE_SIZE + TILE_GAP));
		if (!skippedToResult && tickIndex != lastTickIndex && progress < 1.0)
		{
			lastTickIndex = tickIndex;
			sounds.playTick();
		}
		int firstIndex = Math.max(0, (int) (offset / (TILE_SIZE + TILE_GAP)) - 1);

		Shape previousClip = graphics.getClip();
		graphics.clipRect(reelX, reelY, reelWidth, TILE_SIZE);
		for (int index = firstIndex; index < reel.size(); index++)
		{
			int tileX = reelX + index * (TILE_SIZE + TILE_GAP) - (int) offset;
			if (tileX > reelX + reelWidth || tileX + TILE_SIZE < reelX)
			{
				continue;
			}
			drawRewardTile(graphics, reel.get(index), tileX, reelY, reelRarities.get(index));
		}
		graphics.setClip(previousClip);

		int centerX = reelX + reelWidth / 2;
		graphics.setColor(new Color(255, 205, 54));
		graphics.fillPolygon(new int[]{centerX - 10, centerX + 10, centerX},
			new int[]{reelY - 14, reelY - 14, reelY}, 3);
		graphics.fillPolygon(new int[]{centerX - 10, centerX + 10, centerX},
			new int[]{reelY + TILE_SIZE + 14, reelY + TILE_SIZE + 14, reelY + TILE_SIZE}, 3);

		if (progress >= 1.0)
		{
			animationComplete = true;
			if (!rewardSoundPlayed)
			{
				rewardSoundPlayed = true;
				sounds.playReward();
			}
			long settledFor = Math.max(0L, elapsed - duration);
			drawWinningShowcase(graphics, centerX, reelY + TILE_SIZE / 2, settledFor);
			int resultBaseline = reelY + TILE_SIZE + 88;
			drawResult(graphics, reelX, reelWidth, resultBaseline);
			drawDismissPrompt(graphics, reelX, reelWidth, resultBaseline + 24);
		}
		return null;
	}

	@Override
	public void keyPressed(KeyEvent event)
	{
		if (!active || !config.interactionControl().allowsSpace()
			|| event.getKeyCode() != KeyEvent.VK_SPACE || spaceDown)
		{
			return;
		}
		spaceDown = true;
		event.consume();
		handleAdvanceOrDismiss();
	}

	private void handleAdvanceOrDismiss()
	{
		if (animationComplete)
		{
			clear();
			return;
		}
		if (reel.isEmpty())
		{
			skipToResultRequested = true;
			return;
		}
		skipToResult();
	}

	@Override
	public void keyReleased(KeyEvent event)
	{
		if (event.getKeyCode() == KeyEvent.VK_SPACE)
		{
			spaceDown = false;
		}
	}

	@Override
	public void keyTyped(KeyEvent event)
	{
	}

	@Override
	public MouseEvent mousePressed(MouseEvent event)
	{
		if (!active || event.getButton() != MouseEvent.BUTTON1
			|| !config.interactionControl().allowsLeftClick())
		{
			return event;
		}
		handleAdvanceOrDismiss();
		consumeLeftGesture = true;
		event.consume();
		return event;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent event)
	{
		if (consumeLeftGesture)
		{
			consumeLeftGesture = false;
			event.consume();
		}
		return event;
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent event)
	{
		if (consumeLeftGesture)
		{
			consumeLeftGesture = false;
			event.consume();
		}
		return event;
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent event) { return event; }

	@Override
	public MouseEvent mouseExited(MouseEvent event) { return event; }

	@Override
	public MouseEvent mouseDragged(MouseEvent event)
	{
		if (consumeLeftGesture)
		{
			event.consume();
		}
		return event;
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent event) { return event; }

	private void skipToResult()
	{
		// Set this first so a render already in progress cannot enqueue another
		// landing, opening, or reel sound after the skip input is received.
		skippedToResult = true;
		skipToResultRequested = false;
		dropSoundPlayed = true;
		openSoundPlayed = true;
		lastBounceImpact = 2;
		startedAt = System.currentTimeMillis()
			- CASKET_DROP_DURATION - CASKET_OPENING_DURATION - REEL_DURATION;
	}

	private void drawWinningShowcase(Graphics2D graphics, int centerX, int centerY, long settledFor)
	{
		double progress = Math.min(1.0, settledFor / 320.0);
		double eased = 1.0 - Math.pow(1.0 - progress, 3.0);
		double scale = 1.0 + 0.62 * eased;
		int reelTileCenterX = (int) Math.round(centerX + TILE_SIZE / 2.0 - winningStopPoint);
		int showcaseCenterX = (int) Math.round(reelTileCenterX + (centerX - reelTileCenterX) * eased);
		int backdropWidth = (int) Math.round(TILE_SIZE * scale + 32);
		int backdropHeight = (int) Math.round(TILE_SIZE * scale + 32);

		graphics.setColor(new Color(10, 13, 19, (int) Math.round(175 * eased)));
		graphics.fillRoundRect(showcaseCenterX - backdropWidth / 2, centerY - backdropHeight / 2,
			backdropWidth, backdropHeight, 18, 18);

		Graphics2D showcaseGraphics = (Graphics2D) graphics.create();
		try
		{
			showcaseGraphics.translate(showcaseCenterX, centerY);
			showcaseGraphics.scale(scale, scale);
			showcaseGraphics.translate(-showcaseCenterX, -centerY);
			drawRewardTile(showcaseGraphics, actualLoot,
				showcaseCenterX - TILE_SIZE / 2, centerY - TILE_SIZE / 2);
		}
		finally
		{
			showcaseGraphics.dispose();
		}
	}

	private void drawCasketDrop(Graphics2D graphics, int x, int y, int width, int height, long elapsed)
	{
		BufferedImage openingSheet = getCasketOpeningSheet();
		boolean useCasketSheet = openingSheet != null;
		BufferedImage casket = useCasketSheet
			? openingSheet : itemManager.getImage(casketItemId, 1, false);
		if (casket == null)
		{
			return;
		}
		int sourceWidth = useCasketSheet ? CASKET_SPRITE_FRAME_WIDTH : casket.getWidth();
		int sourceHeight = useCasketSheet ? CASKET_SPRITE_FRAME_HEIGHT : casket.getHeight();

		double fallProgress = Math.min(1.0, elapsed / (double) CASKET_LANDS_AT);
		double acceleratedFall = fallProgress * fallProgress;
		double impactProgress = Math.max(0.0,
			Math.min(1.0, (elapsed - CASKET_LANDS_AT) / (double) (CASKET_DROP_DURATION - CASKET_LANDS_AT)));
		double squashProgress = Math.max(0.0,
			Math.min(1.0, (elapsed - CASKET_LANDS_AT) / 180.0));
		int normalWidth = useCasketSheet
			? CASKET_SPRITE_FRAME_WIDTH * CASKET_SPRITE_SCALE : 270;
		int normalHeight = useCasketSheet
			? CASKET_SPRITE_FRAME_HEIGHT * CASKET_SPRITE_SCALE : 270;
		int squash = (int) Math.round(Math.sin(squashProgress * Math.PI) * 24.0);
		int imageWidth = normalWidth + squash;
		int imageHeight = normalHeight - squash;
		int landingY = y + (height - normalHeight) / 2;
		int startY = y - normalHeight;
		int imageY = (int) Math.round(startY + (landingY - startY) * acceleratedFall) + squash / 2
			+ (useCasketSheet ? CASKET_SPRITE_Y_OFFSET * CASKET_SPRITE_SCALE : 0);
		int imageX = x + (width - imageWidth) / 2;

		Graphics2D dropGraphics = (Graphics2D) graphics.create();
		try
		{
			dropGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			if (impactProgress > 0.0)
			{
				double angle = Math.sin(impactProgress * Math.PI * 3.0)
					* Math.toRadians(9.0) * Math.pow(1.0 - impactProgress, 0.55);
				// Rock around whichever bottom corner is currently planted.
				double pivotX = angle > 0.0 ? imageX + imageWidth : imageX;
				double pivotY = imageY + imageHeight;
				AffineTransform originalTransform = dropGraphics.getTransform();
				dropGraphics.rotate(angle, pivotX, pivotY);
				dropGraphics.drawImage(casket,
					imageX, imageY, imageX + imageWidth, imageY + imageHeight,
					0, 0, sourceWidth, sourceHeight, null);
				dropGraphics.setTransform(originalTransform);
			}
			else
			{
				dropGraphics.drawImage(casket,
					imageX, imageY, imageX + imageWidth, imageY + imageHeight,
					0, 0, sourceWidth, sourceHeight, null);
			}
			if (impactProgress > 0.0)
			{
				int ringWidth = (int) Math.round(90 + squashProgress * 280);
				int alpha = (int) Math.round(130 * (1.0 - squashProgress));
				int casketBottom = useCasketSheet
					? landingY + (CASKET_SPRITE_Y_OFFSET + CASKET_SPRITE_VISIBLE_BOTTOM)
						* CASKET_SPRITE_SCALE
					: landingY + normalHeight;
				dropGraphics.setColor(new Color(255, 205, 54, alpha));
				dropGraphics.setStroke(new BasicStroke(4f));
				dropGraphics.drawOval(x + width / 2 - ringWidth / 2,
					casketBottom - 24, ringWidth, Math.max(8, ringWidth / 8));
			}
		}
		finally
		{
			dropGraphics.dispose();
		}
	}

	private void drawCasketOpening(Graphics2D graphics, int x, int y, int width, int height, long elapsed)
	{
		BufferedImage openingSheet = getCasketOpeningSheet();
		if (openingSheet != null)
		{
			drawSpriteCasketOpening(graphics, x, y, width, height, elapsed, openingSheet);
			return;
		}

		drawLegacyCasketOpening(graphics, x, y, width, height, elapsed);
	}

	private void drawSpriteCasketOpening(Graphics2D graphics, int x, int y, int width, int height,
		long elapsed, BufferedImage openingSheet)
	{
		long animationElapsed = Math.max(0L, elapsed - CASKET_LID_RELEASE_AT);
		int frame = Math.min(CASKET_SPRITE_FRAME_COUNT - 1,
			(int) (animationElapsed * CASKET_SPRITE_FRAME_COUNT
				/ CASKET_SPRITE_ANIMATION_DURATION));
		double opening = frame / (double) (CASKET_SPRITE_FRAME_COUNT - 1);
		double fade = Math.max(0.0, Math.min(1.0, (elapsed - 1_300.0) / 150.0));
		int shake = elapsed >= 350 && elapsed < CASKET_LID_RELEASE_AT
			? (int) Math.round(Math.sin(elapsed * 0.075) * (2.0 + (elapsed - 350) / 100.0)) : 0;
		int imageWidth = CASKET_SPRITE_FRAME_WIDTH * CASKET_SPRITE_SCALE;
		int imageHeight = CASKET_SPRITE_FRAME_HEIGHT * CASKET_SPRITE_SCALE;
		int imageX = x + (width - imageWidth) / 2 + shake;
		int imageY = y + (height - imageHeight) / 2
			+ CASKET_SPRITE_Y_OFFSET * CASKET_SPRITE_SCALE;

		Graphics2D openingGraphics = (Graphics2D) graphics.create();
		try
		{
			openingGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			openingGraphics.setComposite(AlphaComposite.SrcOver.derive((float) (1.0 - fade)));

			if (opening > 0.0)
			{
				int flashAlpha = (int) Math.round(150 * opening);
				openingGraphics.setColor(new Color(255, 232, 145, flashAlpha));
				int burst = (int) Math.round(40
					+ opening * (Math.max(imageWidth, imageHeight) - 6));
				openingGraphics.fillOval(x + width / 2 - burst / 2,
					y + height / 2 - burst / 2, burst, burst);
			}

			int sourceX = frame * CASKET_SPRITE_FRAME_WIDTH;
			openingGraphics.drawImage(openingSheet,
				imageX, imageY, imageX + imageWidth, imageY + imageHeight,
				sourceX, 0, sourceX + CASKET_SPRITE_FRAME_WIDTH,
				CASKET_SPRITE_FRAME_HEIGHT, null);
		}
		finally
		{
			openingGraphics.dispose();
		}
	}

	private void drawLegacyCasketOpening(Graphics2D graphics, int x, int y, int width, int height,
		long elapsed)
	{
		BufferedImage casket = itemManager.getImage(casketItemId, 1, false);
		if (casket == null)
		{
			return;
		}

		double opening = Math.max(0.0, Math.min(1.0, (elapsed - CASKET_LID_RELEASE_AT) / 250.0));
		double fade = Math.max(0.0, Math.min(1.0, (elapsed - 1_200.0) / 250.0));
		int shake = elapsed >= 350 && elapsed < CASKET_LID_RELEASE_AT
			? (int) Math.round(Math.sin(elapsed * 0.075) * (2.0 + (elapsed - 350) / 180.0)) : 0;
		int imageSize = 270;
		int imageX = x + (width - imageSize) / 2 + shake;
		int imageY = y + (height - imageSize) / 2;

		Graphics2D openingGraphics = (Graphics2D) graphics.create();
		try
		{
			openingGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			openingGraphics.setComposite(AlphaComposite.SrcOver.derive((float) (1.0 - fade)));

			int glowSize = imageSize + 34;
			int glowAlpha = (int) (55 + opening * 110);
			openingGraphics.setColor(new Color(255, 205, 54, glowAlpha));
			openingGraphics.fillOval(imageX - 17, imageY - 17, glowSize, glowSize);

			int sourceWidth = casket.getWidth();
			int sourceHeight = casket.getHeight();
			int lidCut = Math.max(1, sourceHeight / 2);
			int destinationCut = imageY + imageSize / 2;
			// Keep the body planted while the upper half lifts to sell the opening.
			openingGraphics.drawImage(casket, imageX, destinationCut,
				imageX + imageSize, imageY + imageSize,
				0, lidCut, sourceWidth, sourceHeight, null);
			int lidLift = (int) Math.round(opening * imageSize * 0.23);
			int lidSpread = (int) Math.round(opening * imageSize * 0.07);
			openingGraphics.drawImage(casket, imageX - lidSpread, imageY - lidLift,
				imageX + imageSize + lidSpread, destinationCut - lidLift,
				0, 0, sourceWidth, lidCut, null);
		}
		finally
		{
			openingGraphics.dispose();
		}

		if (opening > 0.0)
		{
			int flashAlpha = (int) Math.round(150 * opening * (1.0 - fade));
			graphics.setColor(new Color(255, 232, 145, flashAlpha));
			int burst = (int) Math.round(40 + opening * 150);
			graphics.fillOval(x + width / 2 - burst / 2, y + height / 2 - burst / 2, burst, burst);
		}
	}

	private BufferedImage getCasketOpeningSheet()
	{
		switch (casketItemId)
		{
			case ItemID.TRAIL_REWARD_CASKET_BEGINNER: return BEGINNER_CASKET_OPENING_SHEET;
			case ItemID.TRAIL_REWARD_CASKET_EASY: return EASY_CASKET_OPENING_SHEET;
			case ItemID.TRAIL_REWARD_CASKET_MEDIUM: return MEDIUM_CASKET_OPENING_SHEET;
			case ItemID.TRAIL_REWARD_CASKET_HARD: return HARD_CASKET_OPENING_SHEET;
			case ItemID.TRAIL_REWARD_CASKET_ELITE: return ELITE_CASKET_OPENING_SHEET;
			case ItemID.TRAIL_REWARD_CASKET_MASTER: return MASTER_CASKET_OPENING_SHEET;
			default: return null;
		}
	}

	private static BufferedImage loadCasketOpeningSheet(String tier)
	{
		try (InputStream stream = ClueCaseOverlay.class.getResourceAsStream(
			"/reward-casket-" + tier + "-sprite-sheet.png"))
		{
			return stream == null ? null : ImageIO.read(stream);
		}
		catch (IOException ex)
		{
			return null;
		}
	}

	private List<List<Loot>> buildReel(List<Loot> winningLoot, int casketItemId)
	{
		List<List<Loot>> items = new ArrayList<>();
		for (int index = 0; index < REEL_ITEM_COUNT; index++)
		{
			items.add(ClueLootSimulator.generate(casketItemId, rollCounterStrikeRarity()));
		}
		items.set(WINNING_INDEX, winningLoot);
		return items;
	}

	private List<LootRarity> buildReelRarities(List<Loot> winningLoot, int casketItemId)
	{
		List<LootRarity> rarities = new ArrayList<>(REEL_ITEM_COUNT);
		for (int index = 0; index < REEL_ITEM_COUNT; index++)
		{
			rarities.add(ClueLootSimulator.rarityFor(reel.get(index), casketItemId));
		}
		rarities.set(WINNING_INDEX, ClueLootSimulator.rarityFor(winningLoot, casketItemId));
		return rarities;
	}

	static LootRarity rollCounterStrikeRarity()
	{
		double roll = ThreadLocalRandom.current().nextDouble();
		if (roll < 0.79923)
		{
			return LootRarity.MIL_SPEC;
		}
		if (roll < 0.95908)
		{
			return LootRarity.RESTRICTED;
		}
		if (roll < 0.99105)
		{
			return LootRarity.CLASSIFIED;
		}
		if (roll < 0.99744)
		{
			return LootRarity.COVERT;
		}
		return LootRarity.RARE_SPECIAL;
	}

	private static double chooseWinningStopPoint()
	{
		return ThreadLocalRandom.current().nextDouble(3.0, TILE_SIZE - 3.0);
	}

	private void drawFrame(Graphics2D graphics, int x, int y, int width, int height, int reelY)
	{
		graphics.setColor(new Color(8, 11, 17, 150));
		graphics.fillRoundRect(x - 5, y - 5, width + 10, height + 10, 20, 20);
		graphics.setColor(new Color(18, 22, 31, 245));
		graphics.fillRoundRect(x, y, width, height, 16, 16);
		graphics.setColor(new Color(66, 81, 108));
		graphics.setStroke(new BasicStroke(1.5f));
		graphics.drawRoundRect(x, y, width - 1, height - 1, 16, 16);
		graphics.setColor(new Color(45, 57, 79));
		graphics.fillRoundRect(x + PANEL_MARGIN, reelY - 8,
			width - PANEL_MARGIN * 2, TILE_SIZE + 16, 10, 10);
	}

	private void drawTile(Graphics2D graphics, Loot loot, int x, int y)
	{
		drawTile(graphics, loot, x, y,
			ClueLootSimulator.rarityFor(Collections.singletonList(loot), casketItemId));
	}

	private void drawTile(Graphics2D graphics, Loot loot, int x, int y, LootRarity rarity)
	{
		Color rarityColor = rarity.getColor();
		drawTileFrame(graphics, x, y, rarityColor);
		BufferedImage image = itemManager.getImage(loot.getItemId(), loot.getQuantity(), true);
		if (image != null)
		{
			int imageSize = ITEM_ICON_SIZE;
			Object interpolation = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			graphics.drawImage(image, x + (TILE_SIZE - imageSize) / 2,
				y + (TILE_SIZE - 24 - imageSize) / 2,
				imageSize, imageSize, null);
			if (interpolation != null)
			{
				graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
			}
		}

		String name = itemManager.getItemComposition(loot.getItemId()).getName();
		if (name.length() > 15)
		{
			name = name.substring(0, 14) + "…";
		}
		FontMetrics metrics = graphics.getFontMetrics();
		graphics.setColor(Color.WHITE);
		graphics.drawString(name, x + (TILE_SIZE - metrics.stringWidth(name)) / 2,
			y + TILE_SIZE - 10);
	}

	private void drawRewardTile(Graphics2D graphics, List<Loot> rewards, int x, int y)
	{
		drawRewardTile(graphics, rewards, x, y,
			ClueLootSimulator.rarityFor(rewards, casketItemId));
	}

	private void drawRewardTile(Graphics2D graphics, List<Loot> rewards, int x, int y,
		LootRarity rarity)
	{
		if (rewards.isEmpty())
		{
			return;
		}
		if (rewards.size() == 1)
		{
			drawTile(graphics, rewards.get(0), x, y, rarity);
			return;
		}

		Color rarityColor = rarity.getColor();
		drawTileFrame(graphics, x, y, rarityColor);
		int columns = (int) Math.ceil(Math.sqrt(rewards.size()));
		int rows = (int) Math.ceil(rewards.size() / (double) columns);
		int gridPadding = 8;
		int contentHeight = TILE_SIZE - 30;
		int contentWidth = TILE_SIZE - gridPadding * 2;
		int cellWidth = contentWidth / columns;
		int cellHeight = contentHeight / rows;
		int imageSize = ITEM_ICON_SIZE;

		Object interpolation = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		for (int index = 0; index < rewards.size(); index++)
		{
			Loot reward = rewards.get(index);
			BufferedImage image = itemManager.getImage(reward.getItemId(), reward.getQuantity(), true);
			if (image == null)
			{
				continue;
			}

			int column = index % columns;
			int row = index / columns;
			int imageX = x + gridPadding + column * cellWidth + (cellWidth - imageSize) / 2;
			int imageY = y + 3 + row * cellHeight + (cellHeight - imageSize) / 2;
			graphics.drawImage(image, imageX, imageY, imageSize, imageSize, null);
		}
		if (interpolation != null)
		{
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
		}

		long totalValue = 0L;
		boolean hasUnpricedItem = false;
		for (Loot reward : rewards)
		{
			int itemPrice = itemManager.getItemPrice(reward.getItemId());
			if (itemPrice <= 0)
			{
				hasUnpricedItem = true;
			}
			else
			{
				totalValue += (long) itemPrice * reward.getQuantity();
			}
		}
		String label = totalValue == 0L && hasUnpricedItem
			? "Untradeable"
			: formatGp(totalValue, hasUnpricedItem);
		FontMetrics metrics = graphics.getFontMetrics();
		graphics.setColor(new Color(255, 224, 112));
		graphics.drawString(label, x + (TILE_SIZE - metrics.stringWidth(label)) / 2,
			y + TILE_SIZE - 8);
	}

	private static String formatGp(long value, boolean partialValue)
	{
		String suffix = partialValue ? "+ gp" : " gp";
		if (value >= 1_000_000_000L)
		{
			return String.format(Locale.US, "%.2fB%s", value / 1_000_000_000.0, suffix);
		}
		if (value >= 1_000_000L)
		{
			return String.format(Locale.US, "%.2fM%s", value / 1_000_000.0, suffix);
		}
		if (value >= 1_000L)
		{
			return String.format(Locale.US, "%.1fK%s", value / 1_000.0, suffix);
		}
		return String.format(Locale.US, "%,d%s", value, suffix);
	}

	private static void drawTileFrame(Graphics2D graphics, int x, int y, Color borderColor)
	{
		Graphics2D tileGraphics = (Graphics2D) graphics.create();
		try
		{
			tileGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);

			// Layered translucent outlines give the rarity colour a soft bloom.
			tileGraphics.setColor(new Color(borderColor.getRed(), borderColor.getGreen(),
				borderColor.getBlue(), 35));
			tileGraphics.setStroke(new BasicStroke(9f));
			tileGraphics.drawRoundRect(x + 4, y + 4, TILE_SIZE - 8, TILE_SIZE - 8, 9, 9);
			tileGraphics.setColor(new Color(borderColor.getRed(), borderColor.getGreen(),
				borderColor.getBlue(), 75));
			tileGraphics.setStroke(new BasicStroke(5f));
			tileGraphics.drawRoundRect(x + 2, y + 2, TILE_SIZE - 4, TILE_SIZE - 4, 8, 8);

			tileGraphics.setPaint(new GradientPaint(x, y, new Color(48, 59, 80),
				x, y + TILE_SIZE, blend(new Color(24, 29, 40), borderColor, 0.28f)));
			tileGraphics.fillRoundRect(x + 2, y + 2, TILE_SIZE - 4, TILE_SIZE - 4, 7, 7);

			// A saturated lower accent makes the tier recognizable even behind icons.
			tileGraphics.setColor(new Color(borderColor.getRed(), borderColor.getGreen(),
				borderColor.getBlue(), 115));
			tileGraphics.fillRect(x + 3, y + TILE_SIZE - 17, TILE_SIZE - 6, 14);
			tileGraphics.setColor(borderColor);
			tileGraphics.setStroke(new BasicStroke(3f));
			tileGraphics.drawRoundRect(x + 1, y + 1, TILE_SIZE - 2, TILE_SIZE - 2, 8, 8);
		}
		finally
		{
			tileGraphics.dispose();
		}
	}

	private static Color blend(Color base, Color accent, float accentAmount)
	{
		float baseAmount = 1f - accentAmount;
		return new Color(
			Math.round(base.getRed() * baseAmount + accent.getRed() * accentAmount),
			Math.round(base.getGreen() * baseAmount + accent.getGreen() * accentAmount),
			Math.round(base.getBlue() * baseAmount + accent.getBlue() * accentAmount));
	}

	private void drawResult(Graphics2D graphics, int x, int width, int baseline)
	{
		StringBuilder result = new StringBuilder("Received: ");
		for (int index = 0; index < actualLoot.size(); index++)
		{
			Loot loot = actualLoot.get(index);
			if (index > 0)
			{
				result.append(", ");
			}
			result.append(itemManager.getItemComposition(loot.getItemId()).getName());
			if (loot.getQuantity() > 1)
			{
				result.append(" x").append(loot.getQuantity());
			}
		}
		FontMetrics metrics = graphics.getFontMetrics();
		graphics.setColor(new Color(255, 224, 112));
		graphics.drawString(result.toString(), x + (width - metrics.stringWidth(result.toString())) / 2, baseline);
	}

	private void drawDismissPrompt(Graphics2D graphics, int x, int width, int baseline)
	{
		boolean space = config.interactionControl().allowsSpace();
		boolean click = config.interactionControl().allowsLeftClick();
		String prompt;
		if (space && click)
		{
			prompt = "Press SPACE or left-click to close";
		}
		else if (space)
		{
			prompt = "Press SPACE to close";
		}
		else
		{
			prompt = "Left-click to close";
		}
		FontMetrics metrics = graphics.getFontMetrics();
		graphics.setColor(new Color(176, 195, 217));
		graphics.drawString(prompt, x + (width - metrics.stringWidth(prompt)) / 2, baseline);
	}
}
