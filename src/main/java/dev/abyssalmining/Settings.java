package dev.abyssalmining;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

final class Settings {
    static final ModConfigSpec SPEC;
    static final ModConfigSpec.IntValue MINING_BUDGET, BATTLE_BUDGET, SACRIFICE_RADIUS, LOOT_QUANTITY, ME_EXPORT_INTERVAL;
    static final ModConfigSpec.BooleanValue MINE_REDSTONE, SIDE_IO;
    static final ModConfigSpec.ConfigValue<List<? extends String>> LOOT_BLACKLIST;
    static final List<ModConfigSpec.IntValue> DURATIONS;
    static final List<Integer> DEFAULT_SECONDS = List.of(300, 120, 180, 600, 360, 240);
    static final List<String> RITUAL_OUTPUTS = List.of("abyssal_mineshaft", "stack_upgrade", "parallel_upgrade",
        "miner_ancient_abyssal", "advanced_battlefield", "looting_upgrade");

    static {
        var config = new ModConfigSpec.Builder();
        config.push("machines");
        MINING_BUDGET = config.defineInRange("mineshaftMaxWorkBudgetPerTick", 48, 1, 256);
        BATTLE_BUDGET = config.defineInRange("battlefieldMaxWorkBudgetPerTick", 16, 1, 128);
        MINE_REDSTONE = config.define("mineshaftRedstoneControl", true);
        SIDE_IO = config.define("horizontalAutomation", true);
        ME_EXPORT_INTERVAL = config.comment("Server ticks between ME export batches.")
            .defineInRange("meExportIntervalTicks", 10, 1, 1200);
        LOOT_QUANTITY = config.comment("Only limits the bonus quantity. Original loot is always retained.")
            .defineInRange("battlefieldMaxLootQuantityPerOperation", 16384, 1, 1048576);
        LOOT_BLACKLIST = config.defineListAllowEmpty("battlefieldLootMultiplierBlacklist",
            List.of(), () -> "minecraft:experience_bottle",
            entry -> entry instanceof String id && ResourceLocation.tryParse(id) != null);
        config.pop();
        config.push("rituals");
        DURATIONS = List.of(
            config.defineInRange("mineshaftRitualDuration", 6000, 1, 1200000),
            config.defineInRange("stackUpgradeRitualDuration", 2400, 1, 1200000),
            config.defineInRange("parallelUpgradeRitualDuration", 3600, 1, 1200000),
            config.defineInRange("abyssalMinerRitualDuration", 12000, 1, 1200000),
            config.defineInRange("battlefieldRitualDuration", 7200, 1, 1200000),
            config.defineInRange("lootingUpgradeRitualDuration", 4800, 1, 1200000));
        SACRIFICE_RADIUS = config.defineInRange("witherSacrificeRadius", 8, 1, 32);
        SPEC = config.pop().build();
    }

    static int parallel(int upgrades) {
        return (1 << upgrades) - 1;
    }
}
