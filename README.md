# Clue Case Opening

Clue Case Opening is a RuneLite plugin that gives clue-casket and reward-chest loot a CS-style case-opening reveal. Open a casket or loot a supported chest normally and the plugin temporarily hides the standard reward interface, plays an animated reward reel, and then displays the complete loot you actually received.

The fancy new casket animations were created in Aseprite by **Adharmic (aka Goospitter)**.

The animation is cosmetic only: the plugin does not choose, reroll, delay, replace, or otherwise change clue rewards.

<p align="center">
  <a href="https://giphy.com/gifs/FSwHFiJhS479icpBdn">
    <img src="https://media.giphy.com/media/FSwHFiJhS479icpBdn/giphy.gif" alt="Clue Case Opening plugin demonstration" width="800">
  </a>
</p>

## Features

- Supports beginner, easy, medium, hard, elite, and master clue caskets.
- Supports reward chests: Barrows, the Perilous Moons Lunar Chest, Chambers of Xeric, Theatre of Blood, and Tombs of Amascut, each with its own chest animation.
- Captures the real reward from the game's reward container before showing the reveal.
- Animates a raid chest's loot only once, even if you reopen the chest.
- Temporarily hides the standard reward and collection-log notification interfaces to avoid spoilers.
- Plays a casket intro followed by a scrolling reel of simulated clue-reward bundles.
- Generates teaser bundles from bundled OSRS Wiki loot-table data for the matching clue tier or chest.
- Uses source-relative rarity colors based on loot-table rarity, item value, and total bundle value.
- Lets you skip the animation or close the result with Space, left-click, or either input.
- Uses native Old School RuneScape sound effects and respects the game's sound-effects volume.
- Makes no network requests while the plugin is running.

## Configuration

| Setting | Description | Default |
| --- | --- | --- |
| **Animate clue tiers** | Sets the minimum tier to animate: all tiers, medium+, hard+, elite+, or master only. | All tiers |
| **Skip and close control** | Chooses whether Space, left-click, or both control the animation. | Space or left-click |
| **Sound effects** | Enables the native game sounds used by the animation and reel. | On |
| **Hide chat during opening** | Hides the whole chatbox while the animation plays, so loot messages from the game or other plugins (such as loot value announcements) can't spoil the reveal. | On |
| **Reward chests > Barrows chest** | Animates Barrows chest loot. | On |
| **Reward chests > Lunar Chest** | Animates Lunar Chest loot. | On |
| **Reward chests > Raid chests** | Animates Chambers of Xeric, Theatre of Blood, and Tombs of Amascut loot. | On |

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

Replace `hard` with `beginner`, `easy`, `medium`, `elite`, `master`, `barrows`, `lunar`, `cox`, `tob`, or `toa` to preview another source. The preview uses generated teaser loot and does not open or consume anything.

Barrows and the Lunar Chest also accept a partial run: `::cluecase barrows 2 400` previews a run with two brothers killed and a monster combat sum of 400, and `::cluecase lunar 1` previews a single Moon. Without these arguments the preview assumes a full run.

## Loot data

The bundled `clue-loot.csv` resource contains per-roll probabilities, source-table sections, and volume-weighted 24-hour prices derived from the OSRS Wiki. It is used locally to generate believable teaser rewards and rarity bands; it is not used to determine the player's real reward.

The bundled `chest-loot.csv` resource holds the same data for reward chests, scraped from the OSRS Wiki pages for the Barrows chest, Lunar Chest, Ancient chest, Monumental chest, and Tombs of Amascut chest. Raid unique chances in the teaser generator are a typical personal rate and only shape the reel.

Barrows and Lunar Chest teasers follow the run you actually did, read from the game's progress varbits when the chest opens:

- **Barrows:** 1 + brothers killed rolls; each roll has a 1/(450 − 58 × brothers) chance at a piece from a brother you killed, otherwise your reward potential (monster combat sum + 2 × brothers) decides which common item and how many.
- **Lunar Chest:** each Moon you defeated has its own 1/56 chance at a piece of its set; any unique replaces the standard loot, which otherwise rolls 1, 3, or 6 times for 1, 2, or 3 Moons.

For development, the datasets can be refreshed with:

```powershell
.\tools\update-clue-loot.ps1
.\tools\update-chest-loot.ps1
```

The chest opening sprite sheets are generated placeholder pixel art. Regenerate them with `java tools/GenerateChestSprites.java`, or replace any `reward-chest-*-sprite-sheet.png` with hand-drawn art in the same layout: six 48x48 frames in a row, closed first.

## Project structure

- `ClueCasePlugin.java` detects casket opens and chest reward interfaces, captures the real reward, and manages hidden widgets.
- `LootSource.java` lists every supported casket and chest with its interface, container, sprite sheet, and roll rules.
- `ClueCaseOverlay.java` renders the intro, reel, and final reward display.
- `ClueCaseConfig.java` defines the RuneLite configuration options.
- `ClueLootSimulator.java` loads the bundled loot data and generates teaser rewards.
- `ClueCaseSounds.java` plays the animation's native game sounds.
- `tools/update-clue-loot.ps1` and `tools/update-chest-loot.ps1` refresh the development loot datasets.
- `tools/GenerateChestSprites.java` generates the placeholder chest opening sprite sheets.
- `runelite-plugin.properties` contains RuneLite Plugin Hub metadata.

## License

This project is licensed under the terms in [LICENSE](LICENSE).
