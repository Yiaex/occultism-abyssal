package dev.abyssalmining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class BigStackStorage {
    static final int SLOTS = 54;
    private static final int[] STACK_MULTIPLIERS = {1, 16, 256, 4096};
    record Entry(ItemStack item, long count) {}

    private final Entry[] slots = new Entry[SLOTS];

    static int capacity(ItemStack stack, int upgrades) {
        return capacity(stack.getMaxStackSize(), upgrades);
    }

    static int capacity(int nativeLimit, int upgrades) {
        return nativeLimit * STACK_MULTIPLIERS[upgrades];
    }

    Entry entry(int slot) { return slots[slot]; }

    boolean commitExport(int slot, Entry offered, long accepted) {
        if (accepted <= 0) return false;
        if (slots[slot] != offered || accepted > offered.count)
            throw new IllegalStateException("ME export changed the source stack unexpectedly");
        long remainder = offered.count - accepted;
        slots[slot] = remainder == 0 ? null : new Entry(offered.item, remainder);
        return true;
    }

    long count(int slot) {
        return slots[slot] == null ? 0 : slots[slot].count;
    }

    ItemStack read(int slot) {
        Entry entry = slots[slot];
        return entry == null ? ItemStack.EMPTY :
            entry.item.copyWithCount((int) Math.min(entry.count, entry.item.getMaxStackSize()));
    }

    ItemStack extract(int slot, int amount, boolean simulate) {
        if (amount <= 0 || slots[slot] == null) return ItemStack.EMPTY;
        Entry entry = slots[slot];
        int removed = (int) Math.min(Math.min(entry.count, amount), entry.item.getMaxStackSize());
        if (!simulate) slots[slot] = removed == entry.count ? null : new Entry(entry.item, entry.count - removed);
        return entry.item.copyWithCount(removed);
    }

    long insert(Entry entry, int upgrades) {
        long remaining = entry.count;
        ItemStack item = entry.item;
        int capacity = capacity(item, upgrades);
        for (int i = 0; i < SLOTS && remaining > 0; i++) {
            Entry existing = slots[i];
            if (existing == null || !ItemStack.isSameItemSameComponents(existing.item, item)) continue;
            long moved = Math.min(remaining, Math.max(0, capacity - existing.count));
            if (moved > 0) slots[i] = new Entry(existing.item, existing.count + moved);
            remaining -= moved;
        }
        for (int i = 0; i < SLOTS && remaining > 0; i++) {
            if (slots[i] != null) continue;
            long moved = Math.min(remaining, capacity);
            slots[i] = new Entry(item, moved);
            remaining -= moved;
        }
        return remaining;
    }

    static void add(List<Entry> batch, ItemStack stack, long count) {
        if (stack.isEmpty() || count <= 0) return;
        for (int i = 0; i < batch.size(); i++) {
            Entry previous = batch.get(i);
            if (ItemStack.isSameItemSameComponents(previous.item, stack)) {
                batch.set(i, new Entry(previous.item, Math.addExact(previous.count, count)));
                return;
            }
        }
        batch.add(new Entry(stack.copyWithCount(1), count));
    }

    static CompoundTag encode(Entry entry, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("item", entry.item.save(registries));
        tag.putLong("count", entry.count);
        return tag;
    }

    static Entry decode(CompoundTag tag, HolderLookup.Provider registries) {
        ItemStack stack = ItemStack.parseOptional(registries, tag.getCompound("item"));
        long count = tag.getLong("count");
        return stack.isEmpty() || count <= 0 ? null : new Entry(stack.copyWithCount(1), count);
    }

    ListTag save(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (int i = 0; i < SLOTS; i++) {
            if (slots[i] == null) continue;
            CompoundTag tag = encode(slots[i], registries);
            tag.putInt("slot", i);
            list.add(tag);
        }
        return list;
    }

    void load(ListTag list, HolderLookup.Provider registries) {
        Arrays.fill(slots, null);
        for (Tag raw : list) {
            CompoundTag tag = (CompoundTag) raw;
            int slot = tag.getInt("slot");
            if (slot >= 0 && slot < SLOTS) slots[slot] = decode(tag, registries);
        }
    }

    static void drop(Level level, BlockPos pos, Entry entry) {
        long remaining = entry.count;
        int limit = entry.item.getMaxStackSize();
        while (remaining > 0) {
            int count = (int) Math.min(remaining, limit);
            ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                entry.item.copyWithCount(count));
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
            remaining -= count;
        }
    }

    void drain(Level level, BlockPos pos) {
        List<Entry> entries = new ArrayList<>();
        for (Entry entry : slots) if (entry != null) entries.add(entry);
        Arrays.fill(slots, null);
        entries.forEach(entry -> drop(level, pos, entry));
    }
}
