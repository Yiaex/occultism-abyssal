package dev.abyssalmining;

import com.klikli_dev.occultism.common.blockentity.DimensionalMineshaftBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class Machine extends BlockEntity implements MenuProvider {
    static final int SOURCES = 9;
    static final int WEAPON = 9, FUEL = 10, STACK = 11, PARALLEL = 14, LOOT = 17, ME = 20, MAINTENANCE = 21;
    final boolean battlefield;
    final BigStackStorage output = new BigStackStorage();
    final ItemStackHandler input;
    private final Lane[] lanes = new Lane[SOURCES];
    private final IItemHandler top = new Automation(true, false);
    private final IItemHandler bottom = new Automation(false, true);
    private final IItemHandler side = new Automation(true, true);
    private boolean internal;
    private boolean paused;
    private boolean exporting;
    private long lastExportTick = Long.MIN_VALUE;
    private Export export;
    private CompoundTag exportData = new CompoundTag();
    private int roundRobin;
    private long xp;

    interface Export {
        void start(ServerLevel level, CompoundTag data);
        boolean online();
        long insert(BigStackStorage.Entry entry);
        CompoundTag save();
        void close();
    }

    Export export() { return export; }

    private void updateExport(ServerLevel level) {
        boolean enabled = input.getStackInSlot(ME).is(Abyssal.ME.get())
            && net.neoforged.fml.ModList.get().isLoaded("ae2");
        if (enabled && export == null) {
            export = AeExport.create(this);
            export.start(level, exportData);
            invalidateCapabilities();
        } else if (!enabled && export != null) {
            closeExport();
            invalidateCapabilities();
        }
        if (export == null || !export.online()) return;
        long now = level.getGameTime();
        if (lastExportTick != Long.MIN_VALUE && now - lastExportTick < Settings.ME_EXPORT_INTERVAL.get()) return;
        lastExportTick = now;
        exporting = true;
        try {
            for (int slot = 0; slot < BigStackStorage.SLOTS; slot++) {
                var entry = output.entry(slot);
                if (entry != null && output.commitExport(slot, entry, export.insert(entry))) setChanged();
            }
        } finally {
            exporting = false;
        }
    }

    private void closeExport() {
        if (export == null) return;
        exportData = export.save();
        export.close();
        export = null;
        lastExportTick = Long.MIN_VALUE;
    }

    @Override public void setRemoved() {
        closeExport();
        super.setRemoved();
    }

    @Override public void onChunkUnloaded() {
        closeExport();
        super.onChunkUnloaded();
    }

    Machine(BlockPos pos, BlockState state) {
        super(Abyssal.MACHINE.get(), pos, state);
        battlefield = ((MachineBlock) state.getBlock()).battlefield;
        Arrays.setAll(lanes, i -> new Lane());
        input = new ItemStackHandler((battlefield ? MAINTENANCE : ME) + 1) {
            @Override public boolean isItemValid(int slot, ItemStack stack) { return valid(slot, stack); }
            @Override public int getSlotLimit(int slot) { return slot == FUEL ? 64 : 1; }
            @Override protected void onContentsChanged(int slot) {
                if (!internal) {
                    if (slot < SOURCES) lanes[slot].reset();
                    if (slot == WEAPON) for (Lane lane : lanes) lane.resetWork();
                }
                Machine.this.setChanged();
            }
        };
    }

    boolean valid(int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (slot < SOURCES) {
            if (battlefield) return level instanceof ServerLevel server
                ? Battle.target(server, stack) != null : Battle.type(stack) != null;
            return level != null && Mining.valid(level, stack);
        }
        if (slot == WEAPON) return battlefield && Battle.weapon(stack);
        if (slot == FUEL) return battlefield && Battle.fuel(stack);
        if (slot == MAINTENANCE) return battlefield && stack.is(Abyssal.MAINTENANCE.get());
        if (slot == ME) return net.neoforged.fml.ModList.get().isLoaded("ae2") && stack.is(Abyssal.ME.get());
        if (slot < PARALLEL) return stack.is(Abyssal.STACK.get());
        if (slot < LOOT) return stack.is(Abyssal.PARALLEL.get());
        return battlefield && stack.is(Abyssal.LOOT.get());
    }

    int cards(int first) {
        int count = 0;
        for (int i = first; i < first + 3; i++) if (!input.getStackInSlot(i).isEmpty()) count++;
        return count;
    }

    int parallel(int slot) {
        return (input.getStackInSlot(slot).is(Abyssal.MINER.get()) ? 9 : 1) + Settings.parallel(cards(PARALLEL));
    }

    void tick(ServerLevel server) {
        updateExport(server);
        paused = (battlefield || Settings.MINE_REDSTONE.get()) && server.hasNeighborSignal(worldPosition);
        int upgrades = cards(STACK);
        int multiplier = cards(LOOT) + 1;
        boolean changed = battlefield && !paused && repairWeapon(server, input.getStackInSlot(WEAPON));
        internal = true;
        try {
            for (int i = 0; i < lanes.length; i++) {
                Lane lane = lanes[i];
                lane.working = false;
                changed |= flush(lane, upgrades);
                if (paused || !lane.pending.isEmpty()) continue;
                ItemStack source = input.getStackInSlot(i);
                if (source.isEmpty()) continue;
                if (battlefield) {
                    if (!prepareBattle(server, lane, source)) continue;
                    lane.progress = Math.min(lane.progress + 1, lane.interval);
                    if (lane.owed == 0 && lane.progress >= lane.interval) {
                        lane.progress -= lane.interval;
                        lane.owed = parallel(i);
                        lane.task = 0;
                    }
                } else {
                    if (lane.pool == null) {
                        DimensionalMineshaftBlockEntity.forceInitStackNBT(source, server);
                        lane.pool = new Mining.Pool();
                    }
                    if (Mining.protectedByUnusing(server, source) || lane.pool.results(server, source).isEmpty()) continue;
                    lane.duration = Mining.duration(source) + 1;
                    int speed = source.is(Abyssal.MINER.get()) ? 4 : 1;
                    lane.credit = Math.min(64, lane.credit + speed);
                    advanceMining(server, i, lane, source);
                }
                lane.working = true;
                changed = true;
            }
            if (!paused) {
                int budget = battlefield ? Settings.BATTLE_BUDGET.get() : Settings.MINING_BUDGET.get();
                int idle = 0;
                while (budget > 0 && idle < SOURCES) {
                    int index = roundRobin;
                    roundRobin = (roundRobin + 1) % SOURCES;
                    Lane lane = lanes[index];
                    if (lane.owed <= 0 || !lane.pending.isEmpty() ||
                        !(battlefield ? battleHit(server, index, lane, multiplier) : mine(server, index, lane))) {
                        idle++;
                        continue;
                    }
                    budget--;
                    idle = 0;
                    changed = true;
                    flush(lane, upgrades);
                    if (!battlefield && lane.owed == 0 && lane.pending.isEmpty()) {
                        ItemStack source = input.getStackInSlot(index);
                        if (!source.isEmpty()) advanceMining(server, index, lane, source);
                    }
                }
            }
        } finally {
            internal = false;
        }
        if (changed) setChanged();
    }

    private void advanceMining(ServerLevel server, int index, Lane lane, ItemStack source) {
        while (lane.credit > 0 && lane.owed == 0) {
            lane.credit--;
            lane.progress += lane.progress == 0 ? 1 : Mining.advance(server, source);
            if (lane.progress >= lane.duration) {
                lane.progress = 0;
                lane.owed = parallel(index);
            }
        }
    }

    private boolean mine(ServerLevel server, int index, Lane lane) {
        ItemStack source = input.getStackInSlot(index);
        if (lane.pool == null || source.isEmpty() || Mining.protectedByUnusing(server, source)) return false;
        var results = lane.pool.results(server, source);
        if (results.isEmpty()) return false;
        Mining.roll(server, source, results, lane.pending);
        lane.owed--;
        if (source.isEmpty()) lane.reset();
        return true;
    }

    private boolean prepareBattle(ServerLevel server, Lane lane, ItemStack source) {
        ItemStack weapon = input.getStackInSlot(WEAPON);
        ItemStack fuel = input.getStackInSlot(FUEL);
        if (!Battle.weapon(weapon) || Mining.protectedByUnusing(server, weapon)) return false;
        if (!lane.targetChecked) {
            lane.target = Battle.target(server, source);
            lane.targetChecked = true;
        }
        if (lane.target == null || Battle.requiredFuel(source, fuel, lane.target) > fuel.getCount()) return false;
        var timing = Battle.timing(server, lane.target, weapon);
        if (timing == null) return false;
        lane.duration = timing.ticks();
        lane.interval = timing.interval();
        lane.maxHits = timing.hits();
        return true;
    }

    private boolean battleHit(ServerLevel server, int index, Lane lane, int multiplier) {
        ItemStack source = input.getStackInSlot(index);
        if (source.isEmpty() || !prepareBattle(server, lane, source)) return false;
        ItemStack weapon = input.getStackInSlot(WEAPON);
        ItemStack fuel = input.getStackInSlot(FUEL);
        ItemStack used = weapon.copy();
        if (!Battle.hit(server, weapon)) return false;
        int task = lane.task++;
        lane.owed--;
        if (++lane.hits[task] >= lane.maxHits) {
            lane.hits[task] = 0;
            xp += Battle.kill(server, worldPosition, lane.target, source, fuel, used,
                Battle.requiredFuel(source, fuel, lane.target), multiplier, lane.pending);
        }
        repairWeapon(server, weapon);
        if (xp >= 10) {
            BigStackStorage.add(lane.pending, Items.EXPERIENCE_BOTTLE.getDefaultInstance(), xp / 10);
            xp %= 10;
        }
        if (source.isEmpty()) lane.reset();
        if (weapon.isEmpty()) for (Lane other : lanes) other.resetWork();
        return true;
    }

    private boolean repairWeapon(ServerLevel server, ItemStack weapon) {
        if (xp == 0 || !weapon.isDamaged() || !input.getStackInSlot(MAINTENANCE).is(Abyssal.MAINTENANCE.get())
            || Mining.enchantment(server, weapon, Enchantments.MENDING) == 0) return false;
        int repaired = (int) Math.min(weapon.getDamageValue(), xp * 2);
        weapon.setDamageValue(weapon.getDamageValue() - repaired);
        xp -= (repaired + 1L) / 2;
        return true;
    }

    private boolean flush(Lane lane, int upgrades) {
        boolean changed = false;
        for (var iterator = lane.pending.listIterator(); iterator.hasNext();) {
            var entry = iterator.next();
            long remainder = output.insert(entry, upgrades);
            if (remainder == entry.count()) continue;
            changed = true;
            if (remainder == 0) iterator.remove();
            else iterator.set(new BigStackStorage.Entry(entry.item(), remainder));
        }
        return changed;
    }

    int progress(int slot) {
        Lane lane = lanes[slot];
        if (!lane.pending.isEmpty() || lane.owed > 0) return 100;
        if (lane.duration <= 0) return 0;
        if (!battlefield) return (int) Math.min(100, lane.progress * 100 / lane.duration);
        int hit = Arrays.stream(lane.hits, 0, parallel(slot)).max().orElse(0);
        return (int) Math.min(100, (hit * (long) lane.interval + lane.progress) * 100 / lane.duration);
    }

    int status(int slot) {
        if (input.getStackInSlot(slot).isEmpty()) return 0;
        if (paused) return 4;
        if (!lanes[slot].pending.isEmpty()) return 3;
        return lanes[slot].working ? 1 : 2;
    }

    ItemStack extract(int slot, int amount, boolean simulate) {
        ItemStack result = output.extract(slot, amount, simulate);
        if (!simulate && !result.isEmpty()) setChanged();
        return result;
    }

    IItemHandler automation(Direction face) {
        if (face == Direction.UP) return top;
        if (face == Direction.DOWN) return bottom;
        return face == null || Settings.SIDE_IO.get() ? side : null;
    }

    private final class Automation implements IItemHandler {
        private final boolean insert, extract;

        Automation(boolean insert, boolean extract) {
            this.insert = insert;
            this.extract = extract;
        }

        private int inputs() { return insert ? (battlefield ? FUEL + 1 : SOURCES) : 0; }

        @Override public int getSlots() { return inputs() + (extract ? BigStackStorage.SLOTS : 0); }
        @Override public ItemStack getStackInSlot(int slot) {
            return slot < inputs() ? input.getStackInSlot(slot) : output.read(slot - inputs());
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot < inputs() && !isRemoved() ? input.insertItem(slot, stack, simulate) : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot < inputs() || isRemoved() || exporting ? ItemStack.EMPTY : Machine.this.extract(slot - inputs(), amount, simulate);
        }
        @Override public int getSlotLimit(int slot) { return slot < inputs() ? input.getSlotLimit(slot) : 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot < inputs() && valid(slot, stack); }
    }

    void dropInventory() {
        for (int i = 0; i < input.getSlots(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            input.setStackInSlot(i, ItemStack.EMPTY);
            if (!stack.isEmpty()) Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
        }
        output.drain(level, worldPosition);
        for (Lane lane : lanes) {
            for (var entry : lane.pending) BigStackStorage.drop(level, worldPosition, entry);
            lane.pending.clear();
        }
    }

    @Override public Component getDisplayName() { return getBlockState().getBlock().getName(); }

    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MachineMenu(id, inventory, this);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("input", input.serializeNBT(registries));
        tag.put("meNode", export == null ? exportData : export.save());
        tag.put("output", output.save(registries));
        tag.putInt("next", roundRobin);
        tag.putLong("xp", xp);
        ListTag list = new ListTag();
        for (Lane lane : lanes) {
            CompoundTag work = new CompoundTag();
            work.putLong("progress", lane.progress);
            work.putInt("credit", lane.credit);
            work.putInt("owed", lane.owed);
            work.putInt("task", lane.task);
            work.putIntArray("hits", lane.hits);
            ListTag pending = new ListTag();
            for (var entry : lane.pending) pending.add(BigStackStorage.encode(entry, registries));
            work.put("pending", pending);
            list.add(work);
        }
        tag.put("lanes", list);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        CompoundTag savedInput = tag.getCompound("input").copy();
        savedInput.putInt("Size", input.getSlots());
        input.deserializeNBT(registries, savedInput);
        exportData = tag.getCompound("meNode");
        output.load(tag.getList("output", Tag.TAG_COMPOUND), registries);
        roundRobin = Math.floorMod(tag.getInt("next"), SOURCES);
        xp = Math.clamp(tag.getLong("xp"), 0, 9);
        ListTag list = tag.getList("lanes", Tag.TAG_COMPOUND);
        for (int i = 0; i < lanes.length && i < list.size(); i++) {
            Lane lane = lanes[i];
            lane.reset();
            lane.pending.clear();
            CompoundTag work = list.getCompound(i);
            lane.progress = Math.max(0, work.getLong("progress"));
            lane.credit = Math.clamp(work.getInt("credit"), 0, 64);
            lane.task = Math.clamp(work.getInt("task"), 0, 8);
            lane.owed = Math.clamp(work.getInt("owed"), 0, battlefield ? 8 - lane.task : 16);
            int[] savedHits = work.getIntArray("hits");
            System.arraycopy(savedHits, 0, lane.hits, 0, Math.min(savedHits.length, lane.hits.length));
            for (Tag raw : work.getList("pending", Tag.TAG_COMPOUND)) {
                var entry = BigStackStorage.decode((CompoundTag) raw, registries);
                if (entry != null) lane.pending.add(entry);
            }
        }
    }

    private static final class Lane {
        long duration, progress;
        int credit, owed, task, maxHits, interval;
        final int[] hits = new int[8];
        final List<BigStackStorage.Entry> pending = new ArrayList<>();
        Mining.Pool pool;
        Battle.Target target;
        boolean targetChecked, working;

        void resetWork() {
            duration = progress = 0;
            working = false;
            credit = owed = task = 0;
            Arrays.fill(hits, 0);
        }

        void reset() {
            resetWork();
            pool = null;
            target = null;
            targetChecked = false;
        }
    }
}
