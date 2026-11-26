# Occultism: Abyss

[简体中文](README.md)

**Occultism: Abyss 1.3.0** is an Occultism add-on for Minecraft **1.21.1**, NeoForge **21.1.265 up to but excluding 21.2**, Java **21**, and Occultism **1.227.0**. Its mod ID is `occultism_abyssal`. The development environment uses NeoForge **21.1.265**.

## Features

- The Abyssal Dimensional Mineshaft and Advanced Dimensional Battlefield each have nine work slots. Available upgrades include Stack, Parallel, Looting, Weapon Maintenance, and ME Export, depending on the machine.
- Six rituals use Occultism’s native ritual system and the Abyssal Covenant Pentacle to craft machines, upgrade cards, and the Abyssal Ancient Miner.
- Machines support redstone control, sided automation, and optional export to an AE2 ME network. JEI displays ritual recipes and Jade shows actual machine inventory counts.

## Installation

Put `occultism_abyssal-1.3.0.jar` in the `mods` directory on both the client and server. Install Occultism and its required dependencies in each instance. These are the versions used for development and testing.

| Required mod | Version |
| --- | --- |
| [Occultism](https://modrinth.com/mod/occultism/version/SFDVXWYt) | 1.227.0 |
| [Modonomicon](https://modrinth.com/mod/modonomicon/version/B0Pncoub) | 1.123.1 |
| [Curios API](https://modrinth.com/mod/curios/version/KJsZIQrT) | 9.6.0+1.21.1 |
| [GeckoLib](https://modrinth.com/mod/geckolib/version/nxAPSFAR) | 4.9.4 |
| [SmartBrainLib](https://modrinth.com/mod/smartbrainlib/version/O5EpeqI3) | 1.16.11 |

Open the **Abyss** chapter in Occultism’s **Dictionary of Spirits** for all six rituals and a projectable pentacle preview. JEI, Jade 15.10.6, and AE2 are optional. When using AE2, install the dependencies your AE2 version requires. The development setup uses GuideME.

When upgrading from 1.2.3 or earlier, remove the old JAR from the `mods` directory. If you customized the server settings, copy `config/occultism_abyssal_mining-server.toml` to `config/occultism_abyssal-server.toml`. Update recipe paths and references in custom data packs to the `occultism_abyssal` namespace.

## Building the Abyssal Covenant Pentacle

Go to **Abyss → Abyssal Covenant Pentacle** in the Dictionary of Spirits to preview or project the layout.

Build the symmetrical 19×19 pentacle at a single height. Mark each `X` with a rune and put an Eldritch or Celestial Chalice at `C`. Sacrificial Bowls can go on open tiles (`.`).

```text
.........X.........
.....X..X.X..X.....
....X..XXXXX..X....
...X..X..X..X..X...
..X.XX...X...XX.X..
.X..XX...X...XX..X.
...X..X..X..X..X...
..X....X.X.X....X..
.XX.....XXX.....XX.
X.XXXXXXXCXXXXXXX.X
.XX.....XXX.....XX.
..X....X.X.X....X..
...X..X..X..X..X...
.X..XX...X...XX..X.
..X.XX...X...XX.X..
...X..X..X..X..X...
....X..XXXXX..X....
.....X..X.X..X.....
.........X.........
```

- Draw the runes with void chalk, rainbow chalk, or a mix of both. After upgrading an older installation, use the new projection to adjust the layout.
- Keep the bowls at rune height and within nine blocks of the center on both the X and Z axes. The Abyssal Ancient Miner ritual needs ten bowls, with one offering in each.
- Put each ingredient in a separate bowl. Right-click the center chalice with a bound Marid Book of Binding in your main hand. The ritual consumes the book according to Occultism crafting rules.
- For the Abyssal Ancient Miner ritual, a player must kill a Wither within the configured radius of the chalice after activation. The default radius is eight blocks. Offerings remain in their bowls until the sacrifice, then the 600-second countdown begins and the chalice consumes them one at a time.
- The result drops from the chalice. An inverted Sacrificial Bowl can collect it using Occultism’s standard behavior. Breaking a required part interrupts the ritual and returns the ingredients. Rituals started before an upgrade finish on their original core.

## Rituals

Each ritual yields one item. The outputs and recipes use the `occultism_abyssal` namespace. The IDs below are their paths.

| Result / ID | Default duration | Ingredients |
| --- | ---: | --- |
| Abyssal Dimensional Mineshaft / `abyssal_mineshaft` | 300 seconds | `occultism:dimensional_mineshaft` ×1, `occultism:dimensional_matrix` ×1, `occultism:spirit_attuned_gem` ×1, `occultism:iesnium_ingot` ×2, `minecraft:shulker_box` ×1, `minecraft:obsidian` ×1 |
| Stack Upgrade Card / `stack_upgrade` | 120 seconds | `occultism:dimensional_matrix` ×1, `occultism:iesnium_ingot` ×1, `minecraft:shulker_shell` ×2, `minecraft:ender_chest` ×1 |
| Parallel Upgrade Card / `parallel_upgrade` | 180 seconds | `occultism:spirit_attuned_gem` ×1, `occultism:dimensional_matrix` ×1, `occultism:otherworld_essence` ×1, `occultism:iesnium_ingot` ×1, `minecraft:echo_shard` ×1 |
| Abyssal Ancient Miner / `miner_ancient_abyssal` | 600 seconds | `occultism:miner_ancient_eldritch` ×8, `occultism:dimensional_matrix` ×1, `minecraft:netherite_ingot` ×1, plus one `minecraft:wither` sacrifice |
| Advanced Dimensional Battlefield / `advanced_battlefield` | 360 seconds | `occultism:dimensional_battlefield` ×1, `occultism:spirit_attuned_gem` ×1, `occultism:soul_gem` ×1, `occultism:iesnium_ingot` ×1, `minecraft:diamond_sword` ×1, `minecraft:crying_obsidian` ×1 |
| Looting Upgrade Card / `looting_upgrade` | 240 seconds | `occultism:spirit_attuned_gem` ×1, `occultism:soul_gem` ×1, `occultism:otherworld_essence` ×1, `minecraft:diamond` ×1, `minecraft:ender_pearl` ×1 |

All six rituals also require a bound Marid Book of Binding. Use separate bowls for repeated ingredients. The miner recipe uses eight bowls for the Eldritch Ancient Miners, one for the Dimensional Matrix, and one for the Netherite Ingot.

Data packs can override `data/occultism_abyssal/recipe/<id>.json`. The recipe type is `occultism:ritual` and the ritual type is `occultism_abyssal:covenant_craft`. The recipe’s `duration` is in seconds. If it matches the default, the server configuration can override the actual duration in ticks.

## Operating the machines

- The mineshaft runs up to nine miners on independent timers. The battlefield accepts up to nine mob sources and shares a weapon and a fuel slot. Soul Gems are checked against Occultism’s blacklist tags. Spawn eggs act as mob templates, and kills still consume fuel and weapon durability. Higher effective attack damage and main-hand attack speed shorten the interval between hits, down to one tick. The on-hit calculation uses an unenchanted diamond sword as the baseline and includes enchantments that apply to the target.
- Craft the Weapon Maintenance Card with a Looting Upgrade Card, a Bottle o’ Enchanting, and an Occultism Soul Gem in any arrangement. Place the card in the battlefield’s **Mend** slot. Kill XP first repairs a weapon with Mending, up to two durability per point. Every ten surplus XP becomes one Bottle o’ Enchanting.
- Insert miners, mob sources, or eligible battlefield weapons and fuel from the top. The bottom permits output extraction only. Sides allow input and output by default and can be disabled in the server config. Install upgrades by hand.
- Stack levels multiply each item’s native stack size by 1, 16, 256, or 4,096. A standard 64-item stack therefore has capacities of 64, 1,024, 16,384, or 262,144 per slot. Output slots permit extraction only. Right-click to take half a stack, Shift-click to transfer a stack, or use a number key to move items into an empty or compatible hotbar slot. The output slot shows the actual count, and its tooltip also shows the slot limit. Items above the limit remain extractable after a card is removed.
- Parallel levels add 0, 1, 3, or 7 independent operations. The Abyssal Ancient Miner starts with nine. The battlefield’s Looting Upgrade Cards apply a ×1 to ×4 multiplier after the loot calculation for vanilla enchantments.
- A redstone signal pauses a machine. The server can disable redstone control for the mineshaft. Machines pause while their chunks are unloaded and resume when loaded, with no catch-up processing.

## Connecting to an ME network

With AE2 installed, craft the ME Export Upgrade from a Parallel Upgrade Card, an ME Import Bus, and an Engineering Processor. Install it in either machine’s ME slot and connect a cable. The machine uses one channel and exports only when the network has power, a channel, and available storage. By default, it exports one batch every ten game ticks, or about half a second at 20 TPS. Adjust `machines.meExportIntervalTicks` in the server config. The machine retains any items the network rejects.

## Configuration and development

NeoForge generates `config/occultism_abyssal-server.toml`. Modpack authors can use the repository’s [default server configuration](config/occultism_abyssal-server.toml) as a preset. Slot counts, the four upgrade multipliers, and the Abyssal Ancient Miner’s unlimited durability and quadruple speed are fixed. The server synchronizes machine progress, upgrade counts, and inventory totals, and controls ritual durations.

Install **JDK 21** and run the included **Gradle 9.2.1 Wrapper** from the repository root. The first build needs an internet connection to download Gradle and dependencies.

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer
.\gradlew.bat -PwithJei runClient
```

On Linux or macOS, use `./gradlew` for the same tasks. Add `-PwithAe2` or `-PwithJade` to load those optional mods in the development client.

Build outputs are in `build/libs`. Install `occultism_abyssal-1.3.0.jar` in Minecraft. Use the `-sources.jar` to browse the source. Production code and resources are in `src/main`. GameTests and their test-only loot table are in `src/gameTest`.

[English 1.3.0 changelog](CHANGELOG-1.3.0.en-US.md) · [Development notes, test matrix, and compatibility details (Chinese)](DEVELOPMENT.md)
