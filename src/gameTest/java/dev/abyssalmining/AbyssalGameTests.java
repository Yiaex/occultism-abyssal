package dev.abyssalmining;

import com.klikli_dev.occultism.Occultism;
import com.klikli_dev.occultism.common.blockentity.DimensionalMineshaftBlockEntity;
import com.klikli_dev.occultism.common.blockentity.SacrificialBowlBlockEntity;
import com.klikli_dev.occultism.common.blockentity.GoldenSacrificialBowlBlockEntity;
import com.klikli_dev.occultism.crafting.recipe.RitualRecipe;
import com.klikli_dev.occultism.registry.OccultismRecipes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import com.klikli_dev.occultism.registry.OccultismBlocks;
import com.klikli_dev.occultism.registry.OccultismDataComponents;
import com.klikli_dev.occultism.registry.OccultismItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(Abyssal.ID)
@PrefixGameTestTemplate(false)
public final class AbyssalGameTests {
    private static final BlockPos MACHINE = new BlockPos(2, 2, 2);
    private static final BlockPos CENTER = new BlockPos(11, 2, 11);

    @GameTest(template = "empty")
    public static void loadLegacyMachine(GameTestHelper test) {
        var registries = test.getLevel().registryAccess();
        var original = machine(test, false);
        original.output.insert(new BigStackStorage.Entry(new ItemStack(Abyssal.STACK.get()), 262144), 3);

        var blockTag = NbtUtils.writeBlockState(original.getBlockState());
        blockTag.putString("Name", Abyssal.LEGACY_ID + ":abyssal_mineshaft");
        var blockState = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), blockTag);
        check(test, blockState.is(Abyssal.MINE.get()), "Legacy block ID did not resolve through the chunk loader");

        var machineTag = original.saveWithFullMetadata(registries);
        machineTag.putString("id", Abyssal.LEGACY_ID + ":machine");
        machineTag.getList("output", Tag.TAG_COMPOUND).getCompound(0).getCompound("item")
            .putString("id", Abyssal.LEGACY_ID + ":stack_upgrade");
        var loaded = BlockEntity.loadStatic(original.getBlockPos(), blockState, machineTag, registries);
        check(test, loaded instanceof Machine migrated && migrated.output.count(0) == 262144
            && migrated.output.read(0).is(Abyssal.STACK.get()), "Legacy machine or warehouse item did not load");

        var oldWork = new CompoundTag();
        oldWork.putLong("progress", 42);
        oldWork.putInt("remaining", 1);
        var pending = new ListTag();
        pending.add(machineTag.getList("output", Tag.TAG_COMPOUND).getCompound(0).copy());
        oldWork.put("pending", pending);
        var stockTag = new CompoundTag();
        stockTag.put(Abyssal.LEGACY_ID, oldWork);
        var stock = new StockMining();
        stock.load(stockTag, registries);
        var migratedStock = new CompoundTag();
        stock.save(migratedStock, registries);
        var work = migratedStock.getCompound(Abyssal.ID);
        check(test, work.getLong("progress") == 42 && work.getInt("remaining") == 1
            && work.getList("pending", Tag.TAG_COMPOUND).getCompound(0).getLong("count") == 262144,
            "Legacy mining progress or pending output was lost");
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void warehouseAndMenu(GameTestHelper test) {
        Machine machine = machine(test, false);
        for (int count : new int[]{32768, 65535, 65536, 262143, 262144}) {
            var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            var codec = net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket.STREAM_CODEC;
            try {
                codec.encode(buf, new net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket(1, 0, count));
                codec.encode(buf, new net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket(
                    1, MachineMenu.OUTPUT_HIGH, count >>> 16));
                check(test, MachineMenu.joinOutputCount(codec.decode(buf).getValue(), codec.decode(buf).getValue()) == count,
                    "Container packet truncated a large warehouse count");
            } finally {
                buf.release();
            }
        }
        for (int upgrades = 0; upgrades < 4; upgrades++) {
            int expected = new int[]{64, 1024, 16384, 262144}[upgrades];
            check(test, BigStackStorage.capacity(64, upgrades) == expected
                && BigStackStorage.capacity(1, upgrades) == expected / 64, "Wrong per-tier capacity");
        }
        var entry = new BigStackStorage.Entry(new ItemStack(Items.IRON_INGOT), 262144);
        check(test, machine.output.insert(entry, 3) == 0 && machine.output.count(0) == 262144, "262144 capacity");
        check(test, machine.output.insert(new BigStackStorage.Entry(entry.item(), 1), 0) == 0
            && machine.output.count(0) == 262144 && machine.output.count(1) == 1, "Overflow slot accepted more after downgrade");

        var prototype = new ItemStack(Items.DIAMOND_SWORD);
        prototype.setDamageValue(33);
        check(test, machine.output.insert(new BigStackStorage.Entry(prototype, 64), 3) == 0, "Nonstackable expansion failed");
        reload(test, machine);
        check(test, machine.output.count(0) == 262144 && machine.output.count(2) == 64, "Storage NBT counts changed");
        check(test, machine.output.extract(2, 64, true).getCount() == 1
            && machine.output.read(2).getDamageValue() == 33, "Storage lost item components or exposed illegal stack");

        Player player = test.makeMockPlayer(GameType.SURVIVAL);
        Player second = test.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(machine.getBlockPos().getCenter());
        second.moveTo(machine.getBlockPos().getCenter());
        var menu = new MachineMenu(1, player.getInventory(), machine);
        var other = new MachineMenu(2, second.getInventory(), machine);
        check(test, menu.outputCount(0) == 262144 && menu.getSlot(0).getItem().getCount() == 64,
            "Menu must sync the true count separately from the legal ItemStack");
        if (net.neoforged.fml.ModList.get().isLoaded("jade")) {
            var view = AbyssalJade.groups(machine, false).getFirst();
            var clientView = AbyssalJade.clientGroups(List.of(view)).getFirst().views.getFirst();
            check(test, "262144".equals(clientView.amountText), "Jade overlay should render the exact quantity");
            check(test, view.views.getFirst().getCount() == 1 && view.getExtraData().getLongArray("counts")[0] == 262144L,
                "Jade must receive a legal icon stack and the exact count separately");
        }
        menu.clicked(0, 0, ClickType.PICKUP, player);
        other.clicked(0, 3, ClickType.SWAP, second);
        menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
        check(test, menu.getCarried().getCount() == 64 && second.getInventory().getItem(3).getCount() == 64
            && machine.output.count(0) == 262144 - 192, "Menu extraction accounting with two viewers");
        var automation = test.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, machine.getBlockPos(), Direction.DOWN);
        check(test, automation != null && automation.extractItem(0, 1000, false).getCount() == 64
            && machine.output.count(0) == 262144 - 256, "Capability exceeded the legal transfer size");
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void minerSpeedSafetyAndStock(GameTestHelper test) {
        Machine advanced = machine(test, false);
        var server = test.getLevel();
        var normal = new ItemStack(OccultismItems.MINER_ANCIENT_ELDRITCH.get());
        var abyssal = new ItemStack(Abyssal.MINER.get());
        advanced.input.setStackInSlot(0, normal);
        advanced.input.setStackInSlot(1, abyssal);
        for (int i = 2; i < 9; i++) advanced.input.setStackInSlot(i,
            new ItemStack(i % 2 == 0 ? OccultismItems.MINER_DJINNI_ORES.get() : OccultismItems.MINER_AFRIT_DEEPS.get()));
        var stockPos = MACHINE.east(2);
        test.setBlock(stockPos, OccultismBlocks.DIMENSIONAL_MINESHAFT.get());
        DimensionalMineshaftBlockEntity stock = test.getBlockEntity(stockPos);
        var stockMiner = abyssal.copy();
        check(test, stock.inputHandler.insertItem(0, stockMiner, false).isEmpty(), "Stock mine rejected abyssal miner");
        DimensionalMineshaftBlockEntity.forceInitStackNBT(normal, server);
        int cycle = DimensionalMineshaftBlockEntity.getMaxMiningTime(normal) + 1;
        for (int i = 0; i < cycle * 2; i++) {
            advanced.tick(server);
            stock.tick();
        }
        check(test, normal.getDamageValue() == 2, "Unexpected ordinary damage per cycle");
        check(test, abyssal.has(DataComponents.UNBREAKABLE) && abyssal.getDamageValue() == 0
            && abyssal.getMaxDamage() == 0, "Abyssal miner lost unbreakable");
        check(test, total(advanced) > 9 * 8, "Abyssal outputs missing");
        check(test, stock.outputHandler.getStackInSlot(0).getCount() > 0 && stockMiner.getDamageValue() == 0,
            "Stock mine did not mine safely");
        for (int i = 0; i < 3; i++) advanced.input.setStackInSlot(Machine.PARALLEL + i, new ItemStack(Abyssal.PARALLEL.get()));
        check(test, advanced.parallel(0) == 8 && advanced.parallel(1) == 16, "Parallel totals");
        reload(test, advanced);
        check(test, advanced.input.getStackInSlot(1).has(DataComponents.UNBREAKABLE), "Unbreakable missing after NBT reload");
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void miningBackpressure(GameTestHelper test) {
        Machine machine = machine(test, false);
        var diamond = new BigStackStorage.Entry(new ItemStack(Items.DIAMOND), 64L * 54);
        check(test, machine.output.insert(diamond, 0) == 0, "Could not fill output");
        ItemStack miner = new ItemStack(OccultismItems.MINER_ANCIENT_ELDRITCH.get());
        machine.input.setStackInSlot(0, miner);
        DimensionalMineshaftBlockEntity.forceInitStackNBT(miner, test.getLevel());
        int duration = DimensionalMineshaftBlockEntity.getMaxMiningTime(miner) + 1;
        for (int i = 0; i < duration * 3; i++) machine.tick(test.getLevel());
        check(test, miner.getDamageValue() == 1 && machine.status(0) == 3, "Full output rerolled or lost pending work");
        reload(test, machine);
        for (int slot = 0; slot < BigStackStorage.SLOTS; slot++) machine.extract(slot, 64, false);
        machine.tick(test.getLevel());
        check(test, machine.input.getStackInSlot(0).getDamageValue() == 1 && total(machine) > 0,
            "Pending production did not resume transactionally");
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyWarehouseDoesNotStallMining(GameTestHelper test) {
        Machine active = machine(test, false);
        BlockPos controlPos = MACHINE.east(3);
        test.setBlock(controlPos, Abyssal.MINE.get());
        Machine control = test.getBlockEntity(controlPos);
        var source = new ItemStack(OccultismItems.MINER_ANCIENT_ELDRITCH.get());
        var baseline = source.copy();
        active.input.setStackInSlot(0, source);
        control.input.setStackInSlot(0, baseline);
        var level = test.getLevel();
        var automation = level.getCapability(Capabilities.ItemHandler.BLOCK, active.getBlockPos(), Direction.DOWN);
        check(test, automation != null, "Missing automation capability");
        DimensionalMineshaftBlockEntity.forceInitStackNBT(source, level);
        int period = DimensionalMineshaftBlockEntity.getMaxMiningTime(source) + 1;
        long pulled = 0;
        for (int tick = 1; tick <= period * 5; tick++) {
            active.tick(level);
            control.tick(level);
            for (int slot = 0; slot < automation.getSlots(); slot++)
                pulled += automation.extractItem(slot, 64, false).getCount();
            check(test, total(active) == 0 && active.status(0) == 1
                && source.getDamageValue() == baseline.getDamageValue() && source.getDamageValue() == tick / period,
                "Instant extraction changed the mining cycle");
        }
        check(test, pulled > 0, "The immediately drained miner produced no items");
        test.setBlock(MACHINE.west(), Blocks.REDSTONE_BLOCK);
        active.tick(level);
        check(test, active.status(0) == 4 && source.getDamageValue() == 5, "Redstone pause was not reported");
        test.setBlock(MACHINE.west(), Blocks.AIR);
        for (int tick = 0; tick < period; tick++) active.tick(level);
        check(test, active.status(0) == 1 && source.getDamageValue() == 6, "Mining did not resume after redstone");
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void battlefieldWeaponMaintenance(GameTestHelper test) {
        var level = test.getLevel();
        var battlefield = machine(test, true);
        var card = new ItemStack(Abyssal.MAINTENANCE.get());
        var minePosition = MACHINE.east(2);
        test.setBlock(minePosition, Abyssal.MINE.get());
        Machine mine = test.getBlockEntity(minePosition);
        check(test, !mine.valid(Machine.MAINTENANCE, card), "Mineshaft accepted the battlefield card");
        check(test, level.getRecipeManager().byKey(Abyssal.id("maintenance_upgrade")).isPresent(),
            "Maintenance card recipe did not load");

        var source = new ItemStack(Items.BLAZE_SPAWN_EGG);
        var weapon = new ItemStack(Items.DIAMOND_SWORD);
        var modifiers = weapon.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        weapon.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers.withModifierAdded(Attributes.ATTACK_DAMAGE,
            new AttributeModifier(Abyssal.id("test_maintenance_damage"), 100_000, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND));
        weapon.setDamageValue(39);
        var mendingWeapon = weapon.copy();
        mendingWeapon.enchant(level.holderOrThrow(Enchantments.MENDING), 1);
        battlefield.input.setStackInSlot(0, source);
        battlefield.input.setStackInSlot(Machine.FUEL, new ItemStack(OccultismItems.OTHERWORLD_ESSENCE.get(), 64));

        var wear = Occultism.SERVER_CONFIG.itemSettings.butcherHurtChance;
        double originalWear = wear.getAsDouble();
        try {
            wear.set(1.0);
            var unmaintained = mendingWeapon.copy();
            battlefield.input.setStackInSlot(Machine.WEAPON, unmaintained);
            battlefield.tick(level);
            check(test, unmaintained.getDamageValue() == 40 && battleBottles(battlefield) == 1,
                "Mending without the maintenance card changed the default XP output");

            var legacy = state(test, battlefield);
            legacy.getCompound("input").putInt("Size", 21);
            battlefield.loadWithComponents(legacy, level.registryAccess());
            check(test, battlefield.input.getStackInSlot(Machine.WEAPON).getDamageValue() == 40,
                "Adding maintenance lost the weapon in a previous-version save");
            check(test, battlefield.input.insertItem(Machine.MAINTENANCE, card, false).isEmpty(),
                "Battlefield rejected the maintenance card");
            battlefield.input.setStackInSlot(Machine.WEAPON, weapon);
            battlefield.tick(level);
            check(test, weapon.getDamageValue() == 40 && battleBottles(battlefield) == 2,
                "Weapon without Mending used XP for repairs");

            battlefield.input.setStackInSlot(Machine.WEAPON, mendingWeapon);
            battlefield.tick(level);
            check(test, mendingWeapon.getDamageValue() == 20 && battleBottles(battlefield) == 2,
                "Kill XP did not repair the actual weapon before bottling");
            battlefield.tick(level);
            check(test, mendingWeapon.getDamageValue() == 1, "Further kill XP did not repair the weapon");
            battlefield.tick(level);
            check(test, mendingWeapon.getDamageValue() == 0 && state(test, battlefield).getLong("xp") == 9,
                "Unused XP was not carried over after the repair");

            reload(test, battlefield);
            var savedWeapon = battlefield.input.getStackInSlot(Machine.WEAPON);
            battlefield.tick(level);
            check(test, battlefield.input.getStackInSlot(Machine.MAINTENANCE).is(Abyssal.MAINTENANCE.get())
                && savedWeapon.getDamageValue() == 0 && battleBottles(battlefield) == 3
                && state(test, battlefield).getLong("xp") == 8,
                "Partial repair, surplus bottles or saved maintenance state changed after a reload");
        } finally {
            wear.set(originalWear);
        }
        test.succeed();
    }

    private static long battleBottles(Machine battlefield) {
        long bottles = 0;
        for (int slot = 0; slot < BigStackStorage.SLOTS; slot++)
            if (battlefield.output.read(slot).is(Items.EXPERIENCE_BOTTLE)) bottles += battlefield.output.count(slot);
        return bottles;
    }

    @GameTest(template = "empty")
    public static void battlefieldWeaponSpeed(GameTestHelper test) {
        var battlefield = machine(test, true);
        var source = new ItemStack(Items.CHICKEN_SPAWN_EGG);
        var target = Battle.target(test.getLevel(), source);
        var diamond = new ItemStack(Items.DIAMOND_SWORD);
        var netherite = new ItemStack(Items.NETHERITE_SWORD);
        var fast = netherite.copy();
        var modifiers = fast.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        fast.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers.withModifierAdded(Attributes.ATTACK_SPEED,
            new AttributeModifier(Abyssal.id("test_attack_speed"), 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
            EquipmentSlotGroup.MAINHAND));

        int previousInterval = Integer.MAX_VALUE;
        for (var weapon : List.of(diamond, netherite, fast)) {
            var timing = Battle.timing(test.getLevel(), target, weapon);
            check(test, timing.interval() < previousInterval, "Higher main-hand stats did not shorten the hit interval");
            previousInterval = timing.interval();
            weapon.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
            var fuel = new ItemStack(OccultismItems.OTHERWORLD_ESSENCE.get(), 64);
            int fuelCost = Battle.requiredFuel(source, fuel, target);
            battlefield.input.setStackInSlot(0, source.copy());
            battlefield.input.setStackInSlot(Machine.FUEL, fuel);
            battlefield.input.setStackInSlot(Machine.WEAPON, weapon);
            for (long tick = 1; tick < timing.ticks(); tick++) battlefield.tick(test.getLevel());
            check(test, fuel.getCount() == 64, "Battlefield finished a kill early");
            battlefield.tick(test.getLevel());
            check(test, fuel.getCount() == 64 - fuelCost, "Battlefield did not use the new weapon timing");
        }
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void battlefieldInputsFuelAndLoot(GameTestHelper test) {
        Machine battlefield = machine(test, true);
        var eggs = List.of(Items.CHICKEN_SPAWN_EGG, Items.COW_SPAWN_EGG, Items.PIG_SPAWN_EGG,
            Items.SHEEP_SPAWN_EGG, Items.RABBIT_SPAWN_EGG, Items.ZOMBIE_SPAWN_EGG, Items.SKELETON_SPAWN_EGG,
            Items.SPIDER_SPAWN_EGG, Items.CREEPER_SPAWN_EGG);
        for (int i = 0; i < eggs.size(); i++) {
            ItemStack egg = new ItemStack(eggs.get(i));
            check(test, battlefield.valid(i, egg), "Rejected egg in slot " + i);
            battlefield.input.setStackInSlot(i, egg);
        }
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:chicken");
        for (var item : List.of(OccultismItems.SOUL_GEM_ITEM, OccultismItems.FRAGILE_SOUL_GEM_ITEM, OccultismItems.TRINITY_GEM_ITEM)) {
            ItemStack gem = new ItemStack(item.get());
            check(test, !battlefield.valid(0, gem), "Accepted an empty gem");
            gem.set(DataComponents.ENTITY_DATA, CustomData.of(entity));
            check(test, battlefield.valid(0, gem), "Rejected a filled gem: " + item.getId());
        }
        entity.putString("id", "minecraft:player");
        ItemStack illegal = new ItemStack(OccultismItems.TRINITY_GEM_ITEM.get());
        illegal.set(DataComponents.ENTITY_DATA, CustomData.of(entity));
        check(test, !battlefield.valid(0, illegal), "Accepted a player as a source");
        check(test, !battlefield.valid(Machine.WEAPON, new ItemStack(Items.DIRT))
            && !battlefield.valid(Machine.WEAPON, new ItemStack(Items.DIAMOND_CHESTPLATE)), "Weapon accepted junk or armor");

        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        sword.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        battlefield.input.setStackInSlot(Machine.WEAPON, sword);
        for (int i = 0; i < 3; i++) {
            battlefield.input.setStackInSlot(Machine.PARALLEL + i, new ItemStack(Abyssal.PARALLEL.get()));
            battlefield.input.setStackInSlot(Machine.LOOT + i, new ItemStack(Abyssal.LOOT.get()));
        }
        check(test, battlefield.parallel(0) * 9 == 72, "72 parallel tasks unavailable");
        for (int i = 0; i < 100; i++) battlefield.tick(test.getLevel());
        check(test, total(battlefield) == 0, "Loot generated with no fuel");
        ItemStack fuel = new ItemStack(OccultismItems.OTHERWORLD_ESSENCE.get(), 32);
        battlefield.input.setStackInSlot(Machine.FUEL, fuel);
        for (int i = 0; i < 1800; i++) battlefield.tick(test.getLevel());
        check(test, total(battlefield) > 0 && fuel.getCount() < 32, "Expected generated loot and actual fuel usage");
        check(test, !battlefield.input.getStackInSlot(0).isEmpty(), "Spawn egg template was consumed");
        long before = total(battlefield);
        reload(test, battlefield);
        check(test, total(battlefield) == before, "Reload changed loot");
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void covenantRealWitherAndRecovery(GameTestHelper test) {
        var player = ritualPlayer(test);
        putStructure(test, CENTER, OccultismBlocks.ELDRITCH_CHALICE.get(), true);
        var entry = ritual(test, "miner_ancient_abyssal");
        var chalice = startRitual(test, player, CENTER, entry);
        var ritual = entry.value().getRitual();
        check(test, !chalice.sacrificeFulfilled() && chalice.consumedIngredients.isEmpty()
            && ritual.getItemsOnSacrificialBowls(test.getLevel(), chalice.getBlockPos()).size() == 10,
            "Waiting for the Wither must leave all ten offerings in their bowls");
        chalice.tick();
        check(test, elapsed(test, chalice) == 0, "Countdown started before sacrifice");
        var outside = test.spawn(EntityType.WITHER, CENTER.offset(9, 0, 0));
        outside.hurt(player.damageSources().playerAttack(player), 10000);
        check(test, !chalice.sacrificeFulfilled(), "Out-of-range sacrifice accepted");
        var wither = test.spawn(EntityType.WITHER, CENTER.offset(1, 0, 1));
        wither.hurt(player.damageSources().playerAttack(player), 10000);
        check(test, chalice.sacrificeFulfilled(), "Real Wither death ignored");
        for (int tick = 0; tick < 1099; tick++) chalice.tick();
        check(test, chalice.consumedIngredients.isEmpty(), "First offering was consumed early");
        chalice.tick();
        check(test, chalice.consumedIngredients.size() == 1
            && ritual.getItemsOnSacrificialBowls(test.getLevel(), chalice.getBlockPos()).size() == 9,
            "First native interval must consume exactly one offering");
        var saved = state(test, chalice);
        var fresh = new GoldenSacrificialBowlBlockEntity(chalice.getBlockPos(), chalice.getBlockState());
        fresh.loadWithComponents(saved, test.getLevel().registryAccess());
        check(test, fresh.consumedIngredients.size() == 1 && fresh.sacrificeProvided
            && saved.getInt("occultism_abyssal:elapsed") == 1100, "Escrow, Wither or tick state did not persist");
        var legacy = saved.copy();
        legacy.putString("currentRitual", Abyssal.LEGACY_ID + ":miner_ancient_abyssal");
        legacy.putInt(Abyssal.LEGACY_ID + ":elapsed", legacy.getInt(Abyssal.ID + ":elapsed"));
        legacy.remove(Abyssal.ID + ":elapsed");
        chalice.loadWithComponents(legacy, test.getLevel().registryAccess());
        check(test, entry.id().equals(chalice.currentRitualRecipeId) && chalice.consumedIngredients.size() == 1
            && chalice.sacrificeProvided && elapsed(test, chalice) == 1100, "Legacy chalice state did not migrate");
        test.setBlock(CENTER.west(), Blocks.AIR);
        chalice.tick();
        check(test, chalice.getCurrentRitualRecipe() == null, "Broken structure did not cancel");
        test.assertItemEntityCountIs(OccultismItems.MINER_ANCIENT_ELDRITCH.get(), CENTER, 3, 1);
        check(test, ritual.getItemsOnSacrificialBowls(test.getLevel(), chalice.getBlockPos()).size() == 9,
            "Breaking the structure must leave unused offerings in place");
        test.assertItemEntityNotPresent(Abyssal.MINER.get(), CENTER, 3);
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void covenantChalkChaliceAndCompletion(GameTestHelper test) {
        var player = ritualPlayer(test);
        putStructure(test, CENTER, OccultismBlocks.CELESTIAL_CHALICE.get(), false);
        var entry = ritual(test, "stack_upgrade");
        var pattern = entry.value().getPentacle();
        BlockPos pos = test.absolutePos(CENTER);
        var rune = CENTER.west();
        test.setBlock(rune, OccultismBlocks.CHALK_GLYPH_WHITE.get());
        check(test, pattern.getSize().getX() == 19 && pattern.getSize().getZ() == 19, "Expected a 19 x 19 pentacle");
        check(test, pattern.validate(test.getLevel(), pos) == null, "White chalk validated");
        test.setBlock(rune, Abyssal.RUNE.get());
        test.setBlock(CENTER.offset(Covenant.FOCUS), Abyssal.ANCHOR.get());
        check(test, pattern.validate(test.getLevel(), pos) != null, "Legacy glyphs were not recognized");
        var vanillaConflict = test.getLevel().getRecipeManager().getAllRecipesFor(OccultismRecipes.RITUAL_TYPE.get()).stream()
            .filter(recipe -> recipe.id().getNamespace().equals("occultism"))
            .map(recipe -> recipe.value().getPentacle()).filter(java.util.Objects::nonNull)
            .anyMatch(stock -> stock.validate(test.getLevel(), pos) != null);
        check(test, !vanillaConflict, "A stock Occultism pentacle also recognized the Abyss layout");
        test.setBlock(CENTER, OccultismBlocks.GOLDEN_SACRIFICIAL_BOWL.get());
        check(test, pattern.validate(test.getLevel(), pos) == null, "Ordinary golden bowl validated");
        test.setBlock(CENTER, OccultismBlocks.CELESTIAL_CHALICE.get());
        var chalice = startRitual(test, player, CENTER, entry);
        for (int i = 0; i < 2399; i++) chalice.tick();
        check(test, chalice.getCurrentRitualRecipe() != null && elapsed(test, chalice) == 2399, "Celestial chalice finished early");
        chalice.tick();
        test.assertItemEntityCountIs(Abyssal.STACK.get(), CENTER, 3, 1);
        check(test, chalice.getCurrentRitualRecipe() == null, "Ritual did not settle");
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void oneWitherChoosesNearestPentacle(GameTestHelper test) {
        var player = ritualPlayer(test);
        var near = new BlockPos(5, 2, 11);
        var far = new BlockPos(17, 4, 11);
        var entry = ritual(test, "miner_ancient_abyssal");
        putStructure(test, far, OccultismBlocks.ELDRITCH_CHALICE.get(), true);
        putStructure(test, near, OccultismBlocks.ELDRITCH_CHALICE.get(), true);
        var further = startRitual(test, player, far, entry);
        var nearer = startRitual(test, player, near, entry);
        var wither = test.spawn(EntityType.WITHER, new BlockPos(10, 2, 11));
        var damage = player.damageSources().playerAttack(player);
        wither.hurt(damage, 10000);
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(wither, damage));
        check(test, nearer.sacrificeFulfilled() && !further.sacrificeFulfilled(), "Wrong or duplicate Wither assignment");
        for (int tick = 0; tick < 11999; tick++) nearer.tick();
        test.assertItemEntityNotPresent(Abyssal.MINER.get(), near, 3);
        nearer.tick();
        test.assertItemEntityCountIs(Abyssal.MINER.get(), near, 3, 1);
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void abyssBookChapter(GameTestHelper test) {
        var book = com.klikli_dev.modonomicon.data.BookDataManager.get().getBook(
            net.minecraft.resources.ResourceLocation.parse("occultism:dictionary_of_spirits"));
        check(test, book != null && book.getBuildError() == null, "Spirit dictionary did not load");
        var category = book.getCategory(net.minecraft.resources.ResourceLocation.parse("occultism:abyssal"));
        check(test, category != null, "Abyss category did not load");
        check(test, category.getEntries().size() == 7, "Unexpected entries: " + category.getEntries().keySet());
        for (var name : Settings.RITUAL_OUTPUTS)
            check(test, category.getEntries().keySet().stream().anyMatch(id -> id.getPath().endsWith("/" + name)),
                "Missing native recipe page for " + name);
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void allFiveNonSacrificeRecipes(GameTestHelper test) {
        var player = ritualPlayer(test);
        putStructure(test, CENTER, OccultismBlocks.ELDRITCH_CHALICE.get(), false);
        var lookup = test.getLevel().registryAccess();
        for (var id : Settings.RITUAL_OUTPUTS) {
            var entry = ritual(test, id);
            if (entry.value().requiresSacrifice()) continue;
            var chalice = startRitual(test, player, CENTER, entry);
            int ticks = ((NativeCovenantRitual) entry.value().getRitual()).durationTicks(test.getLevel());
            for (int i = 0; i < ticks; i++) chalice.tick();
            check(test, chalice.getCurrentRitualRecipe() == null, "Ritual still running: " + id);
            test.assertItemEntityCountIs(entry.value().getResultItem(lookup).getItem(), CENTER, 3, 1);
        }
        test.succeed();
    }

    private static ServerPlayer ritualPlayer(GameTestHelper test) {
        var level = test.getLevel();
        var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "CovenantTest");
        return new ServerPlayer(level.getServer(), level, profile, net.minecraft.server.level.ClientInformation.createDefault()) {
            @Override public void displayClientMessage(net.minecraft.network.chat.Component text, boolean overlay) {}
            @Override public void sendSystemMessage(net.minecraft.network.chat.Component text) {}
        };
    }

    private static int elapsed(GameTestHelper test, GoldenSacrificialBowlBlockEntity chalice) {
        return state(test, chalice).getInt("occultism_abyssal:elapsed");
    }

    private static RecipeHolder<RitualRecipe> ritual(GameTestHelper test, String name) {
        return test.getLevel().getRecipeManager().getAllRecipesFor(OccultismRecipes.RITUAL_TYPE.get())
            .stream().filter(entry -> entry.id().equals(Abyssal.id(name))).findFirst()
            .orElseThrow(() -> new AssertionError("Ritual not loaded: " + name));
    }

    private static GoldenSacrificialBowlBlockEntity startRitual(GameTestHelper test, ServerPlayer player, BlockPos center,
                                                              RecipeHolder<RitualRecipe> entry) {
        var recipe = entry.value();
        var layout = recipe.getPentacle().simulate(test.getLevel(), BlockPos.ZERO,
            net.minecraft.world.level.block.Rotation.NONE, false, false);
        var offsets = layout.getSecond().stream().filter(cell -> cell.getCharacter() == '_')
            .map(cell -> cell.getWorldPosition()).sorted(java.util.Comparator.comparingInt(
                (BlockPos pos) -> -Math.max(Math.abs(pos.getX()), Math.abs(pos.getZ())))).toList();
        check(test, offsets.size() > 10 && Math.abs(offsets.getFirst().getX()) == 9,
            "Expected movable bowl positions at the 19 x 19 boundary");
        for (int i = 0; i < recipe.getIngredients().size(); i++)
            test.setBlock(center.offset(offsets.get(i)), OccultismBlocks.SACRIFICIAL_BOWL.get());
        for (int i = 0; i < recipe.getIngredients().size(); i++)
            offer(test, center.offset(offsets.get(i)), recipe.getIngredients().get(i).getItems()[0].copyWithCount(1));
        player.setItemInHand(InteractionHand.MAIN_HAND, recipe.getActivationItemStack()[0].copyWithCount(1));
        GoldenSacrificialBowlBlockEntity chalice = test.getBlockEntity(center);
        check(test, recipe.getPentacle() != null && recipe.getPentacle().validate(test.getLevel(), chalice.getBlockPos()) != null,
            "Native pentacle failed validation: " + entry.id());
        var ritual = recipe.getRitual();
        check(test, ritual.isValid(test.getLevel(), chalice.getBlockPos(), chalice, player, player.getMainHandItem(), recipe.getIngredients()),
            "Native inputs did not match: " + ritual.getItemsOnSacrificialBowls(test.getLevel(), chalice.getBlockPos()));
        chalice.activate(test.getLevel(), chalice.getBlockPos(), player, InteractionHand.MAIN_HAND, Direction.UP);
        check(test, chalice.getCurrentRitualRecipe() != null && chalice.getCurrentRitualRecipe().id().equals(entry.id()),
            "Chalice did not start the native ritual: " + entry.id());
        return chalice;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void realHoppers(GameTestHelper test) {
        Machine machine = machine(test, false);
        BlockPos above = MACHINE.above();
        BlockPos below = MACHINE.below();
        test.setBlock(above, Blocks.HOPPER);
        test.setBlock(below, Blocks.HOPPER);
        var input = (net.minecraft.world.level.block.entity.HopperBlockEntity) test.getBlockEntity(above);
        var output = (net.minecraft.world.level.block.entity.HopperBlockEntity) test.getBlockEntity(below);
        input.setItem(0, new ItemStack(Items.DIRT, 16));
        input.setItem(1, new ItemStack(OccultismItems.MINER_DJINNI_ORES.get()));
        machine.output.insert(new BigStackStorage.Entry(new ItemStack(Items.GOLD_INGOT), 128), 1);
        test.succeedWhen(() -> check(test, machine.input.getStackInSlot(0).is(OccultismItems.MINER_DJINNI_ORES)
            && input.getItem(0).getCount() == 16 && output.getItem(0).is(Items.GOLD_INGOT)
            && machine.output.count(0) == 128 - output.getItem(0).getCount(), "Hopper transaction failed"));
    }

    @GameTest(template = "empty")
    public static void lootDataPackAndWeaponLooting(GameTestHelper test) {
        var level = test.getLevel();
        var source = new ItemStack(Items.SILVERFISH_SPAWN_EGG);
        var target = Battle.target(level, source);
        check(test, target != null, "Silverfish is unavailable");
        var sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(level.holderOrThrow(net.minecraft.world.item.enchantment.Enchantments.LOOTING), 3);
        var original = Settings.LOOT_BLACKLIST.get();
        try {
            Settings.LOOT_BLACKLIST.set(List.of("minecraft:diamond"));
            for (int multiplier = 1; multiplier <= 4; multiplier++) {
                List<BigStackStorage.Entry> drops = new java.util.ArrayList<>();
                Battle.kill(level, test.absolutePos(MACHINE), target, source, ItemStack.EMPTY,
                    sword, 0, multiplier, drops);
                check(test, count(drops, Items.AMETHYST_SHARD) == multiplier * 4L,
                    "Expected the loot table to apply Looting III exactly once before the multiplier");
                check(test, count(drops, Items.DIAMOND) == 1L, "Blacklist let diamond multiply");
                check(test, count(drops, Items.EXPERIENCE_BOTTLE) == 1L, "XP bottle multiplied");
                check(test, count(drops, Items.IRON_CHESTPLATE) == multiplier
                    && drops.stream().filter(entry -> entry.item().is(Items.IRON_CHESTPLATE))
                        .allMatch(entry -> entry.item().getCount() == 1 && entry.item().getDamageValue() > 0),
                    "Nonstackable loot was discarded or lost its components");
            }
        } finally {
            Settings.LOOT_BLACKLIST.set(original);
        }
        test.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void aeExportToPoweredDrive(GameTestHelper test) {
        boolean ae = net.neoforged.fml.ModList.get().isLoaded("ae2");
        check(test, test.getLevel().getRecipeManager().byKey(Abyssal.id("me_export_upgrade")).isPresent() == ae,
            "ME recipe must load only when AE2 is installed");
        if (!ae) { test.succeed(); return; }
        AeNetworkGameTest.checkNetwork(test);
    }

    private static long count(List<BigStackStorage.Entry> drops, net.minecraft.world.item.Item item) {
        return drops.stream().filter(entry -> entry.item().is(item)).mapToLong(BigStackStorage.Entry::count).sum();
    }

    private static Machine machine(GameTestHelper test, boolean battlefield) {
        test.setBlock(MACHINE, (battlefield ? Abyssal.ARENA : Abyssal.MINE).get());
        return test.getBlockEntity(MACHINE);
    }

    private static void putStructure(GameTestHelper test, BlockPos center, net.minecraft.world.level.block.Block chalice, boolean wither) {
        test.setBlock(center, chalice);
        var pattern = com.klikli_dev.modonomicon.api.ModonomiconAPI.get().getMultiblock(Abyssal.id("abyssal_covenant"));
        for (var cell : pattern.simulate(test.getLevel(), BlockPos.ZERO,
            net.minecraft.world.level.block.Rotation.NONE, false, false).getSecond()) {
            if (cell.getCharacter() != 'G') continue;
            var pos = center.offset(cell.getWorldPosition());
            test.setBlock(pos.below(), Blocks.STONE);
            test.setBlock(pos, ((pos.getX() + pos.getZ()) % 2 == 0
                ? OccultismBlocks.CHALK_GLYPH_VOID : OccultismBlocks.CHALK_GLYPH_RAINBOW).get());
        }
    }

    private static void offer(GameTestHelper test, BlockPos pos, ItemStack stack) {
        SacrificialBowlBlockEntity bowl = test.getBlockEntity(pos);
        bowl.itemStackHandler.setStackInSlot(0, stack);
    }

    static CompoundTag state(GameTestHelper test, net.minecraft.world.level.block.entity.BlockEntity entity) {
        return entity.saveWithoutMetadata(test.getLevel().registryAccess());
    }

    private static void reload(GameTestHelper test, net.minecraft.world.level.block.entity.BlockEntity entity) {
        entity.loadWithComponents(state(test, entity), test.getLevel().registryAccess());
    }

    static long total(Machine machine) {
        long count = 0;
        for (int i = 0; i < BigStackStorage.SLOTS; i++) count += machine.output.count(i);
        return count;
    }

    static void check(GameTestHelper test, boolean condition, String message) {
        if (!condition) test.fail(message);
    }
}
