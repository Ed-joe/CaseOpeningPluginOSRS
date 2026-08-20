# Clue Case Opening

A RuneLite Plugin Hub plugin that presents clue-casket rewards through a case-opening animation. It observes the normal game interaction and never chooses, delays, replaces, or changes the loot.

## Features

- Detects the normal **Open** action on beginner through master clue caskets.
- Reads the complete reward from the clue reward container before it enters the inventory.
- Covers the game viewport during the reveal so the reward and collection-log interfaces do not spoil it.
- Shows a casket intro, a scrolling reel of realistic teaser bundles, and the complete actual reward.
- Generates teaser rewards from bundled OSRS Wiki loot tables for the matching clue tier.
- Calculates tier-relative rarity colors using table position, stabilized GE values, and total bundle value.
- Supports Space and left-click controls for skipping and closing.
- Uses native Old School RuneScape sound effects.

## Configuration

- **Animate clue tiers** selects the lowest clue tier that receives the animation.
- **Skip and close control** selects Space, left-click, or both.
- **Sound effects** enables or disables animation audio.

The bundled loot data contains the OSRS Wiki's per-roll probabilities, source-table sections, and volume-weighted 24-hour prices. The optional local updater refreshes this development resource; the installed plugin performs no network requests.

## Run it locally

RuneLite Plugin Hub plugins use Gradle and Java 11. This project includes the Gradle wrapper and is pinned to Java 11, so run this from the project folder:

```powershell
.\gradlew.bat run
```

That launches a separate development RuneLite client with this plugin on its classpath. Enable **Clue Case Opening** in the client's plugin list, then open a clue casket normally.

In developer mode, `::cluecase hard` previews a simulated opening. The supported tiers are `beginner`, `easy`, `medium`, `hard`, `elite`, and `master`.

## Project map

- `ClueCasePlugin.java` - listens to client events and calculates the received loot.
- `ClueCaseOverlay.java` - draws and animates the case-opening interface.
- `ClueCaseConfig.java` - defines the RuneLite settings panel.
- `ClueLootSimulator.java` - loads bundled loot data and generates teaser rewards.
- `tools/update-clue-loot.ps1` - refreshes the bundled development dataset.
- `runelite-plugin.properties` - metadata required for Plugin Hub packaging.

## Publish later

Publish the project in a public GitHub repository, then submit its repository URL and commit hash to the RuneLite Plugin Hub. The plugin should remain a cosmetic display that follows the ordinary casket interaction.
