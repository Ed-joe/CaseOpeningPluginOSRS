# Clue Case Opening

Clue Case Opening is a RuneLite plugin that gives clue-casket rewards a CS-style case-opening reveal. Open a casket normally and the plugin temporarily hides the standard reward interface, plays an animated reward reel, and then displays the complete loot you actually received.

The animation is cosmetic only: the plugin does not choose, reroll, delay, replace, or otherwise change clue rewards.

<p align="center">
  <a href="https://giphy.com/gifs/FSwHFiJhS479icpBdn">
    <img src="https://media.giphy.com/media/FSwHFiJhS479icpBdn/giphy.gif" alt="Clue Case Opening plugin demonstration" width="800">
  </a>
</p>

## Features

- Supports beginner, easy, medium, hard, elite, and master clue caskets.
- Captures the real reward from RuneLite's clue reward container before showing the reveal.
- Temporarily hides the standard reward and collection-log notification interfaces to avoid spoilers.
- Plays a casket intro followed by a scrolling reel of simulated clue-reward bundles.
- Generates teaser bundles from bundled OSRS Wiki loot-table data for the matching clue tier.
- Uses tier-relative rarity colors based on loot-table rarity, item value, and total bundle value.
- Lets you skip the animation or close the result with Space, left-click, or either input.
- Uses native Old School RuneScape sound effects and respects the game's sound-effects volume.
- Makes no network requests while the plugin is running.

## Configuration

| Setting | Description | Default |
| --- | --- | --- |
| **Animate clue tiers** | Sets the minimum tier to animate: all tiers, medium+, hard+, elite+, or master only. | All tiers |
| **Skip and close control** | Chooses whether Space, left-click, or both control the animation. | Space or left-click |
| **Sound effects** | Enables the native game sounds used by the animation and reel. | On |

## Running locally

This project requires Java 11 and includes the Gradle wrapper. From the repository root, run:

```powershell
.\gradlew.bat run
```

This starts a separate RuneLite development client with the plugin loaded. Enable **Clue Case Opening** in the plugin list and open a clue casket normally.

Developer mode also provides a simulated preview command:

```text
::cluecase hard
```

Replace `hard` with `beginner`, `easy`, `medium`, `elite`, or `master` to preview another tier. The preview uses generated teaser loot and does not open or consume a casket.

## Loot data

The bundled `clue-loot.csv` resource contains per-roll probabilities, source-table sections, and volume-weighted 24-hour prices derived from the OSRS Wiki. It is used locally to generate believable teaser rewards and rarity bands; it is not used to determine the player's real reward.

For development, the dataset can be refreshed with:

```powershell
.\tools\update-clue-loot.ps1
```

## Project structure

- `ClueCasePlugin.java` detects casket opens, captures the real reward, and manages hidden widgets.
- `ClueCaseOverlay.java` renders the intro, reel, and final reward display.
- `ClueCaseConfig.java` defines the RuneLite configuration options.
- `ClueLootSimulator.java` loads the bundled loot data and generates teaser rewards.
- `ClueCaseSounds.java` plays the animation's native game sounds.
- `tools/update-clue-loot.ps1` refreshes the development loot dataset.
- `runelite-plugin.properties` contains RuneLite Plugin Hub metadata.

## License

This project is licensed under the terms in [LICENSE](LICENSE).
