# Occultism: Abyss 1.3.0 Changelog

Cumulative changes from 1.2.2 through 1.3.0.

## Advanced Dimensional Battlefield

- Higher effective attack damage and main-hand attack speed shorten the interval between hits. An unenchanted diamond sword sets the baseline. Damage enchantments count when they apply to the target, and the minimum interval is one game tick. This rule applies only to the Advanced Dimensional Battlefield.
- Added the **Weapon Maintenance Card** and a dedicated maintenance slot. With the card installed, XP from kills repairs the equipped weapon if it has Mending. Each XP point restores up to 2 durability. Every 10 surplus XP still produces one Bottle o’ Enchanting.
- Craft the maintenance card in any arrangement using a **Looting Upgrade Card, a Bottle o’ Enchanting, and an Occultism Soul Gem**. The Advanced Dimensional Battlefield entry in the Dictionary of Spirits explains how to craft and use it.

## Storage and AE2

- Increased Stack Upgrade Card capacities. For items that normally stack to 64, slot capacities with zero to three cards are **64, 1,024, 16,384, and 262,144**.
- Fixed inventory counts above 65,535 being truncated when synced to the machine screen.
- ME export now runs in batches every **10 game ticks** by default. Server owners can adjust the interval with `machines.meExportIntervalTicks`.

## Textures and Display

- Redrew the tops and sides of both machines and five item icons in 32×32 pixel art. Rewrote the Chinese and English item tooltips.
- The mod list and the mod label at the bottom of item tooltips now show **Occultism: Abyss**. Item names and descriptions still follow the game language.

## Upgrading from 1.2.3 or Earlier

- The mod ID and resource namespace changed from `occultism_abyssal_mining` to `occultism_abyssal`. Remove the old JAR before installing `occultism_abyssal-1.3.0.jar`.
- Added save compatibility for legacy IDs, covering placed blocks, items, stored inventory, pending mineshaft output, and rituals in progress.
- For custom settings, copy `config/occultism_abyssal_mining-server.toml` to `config/occultism_abyssal-server.toml`. Update custom data pack recipe paths and references to the new namespace.
