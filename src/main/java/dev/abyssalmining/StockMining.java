package dev.abyssalmining;

import com.klikli_dev.occultism.common.blockentity.DimensionalMineshaftBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class StockMining {
    private final List<BigStackStorage.Entry> pending = new ArrayList<>();
    private Mining.Pool pool;
    private long progress;
    private int remaining;

    public interface Access {
        StockMining abyssalMining$state();
    }

    public boolean tick(DimensionalMineshaftBlockEntity shaft, ServerLevel level, Supplier<IItemHandler> output) {
        ItemStack miner = shaft.inputHandler.getStackInSlot(0);
        if (!pending.isEmpty()) flush(shaft, output.get());
        if (!miner.is(Abyssal.MINER.get())) {
            if (pool != null || remaining > 0 || progress > 0) {
                remaining = 0;
                progress = 0;
                pool = null;
                shaft.miningTime = 0;
                shaft.setChanged();
            }
            return false;
        }

        if (pool == null) {
            DimensionalMineshaftBlockEntity.forceInitStackNBT(miner, level);
            pool = new Mining.Pool();
        }
        var results = pool.results(level, miner);
        int duration = (int) Math.min(Integer.MAX_VALUE - 1L, Mining.duration(miner) + 1);
        shaft.maxMiningTime = duration;
        shaft.rollsPerOperation = DimensionalMineshaftBlockEntity.getRollsPerOperation(miner);
        if (!level.hasNeighborSignal(shaft.getBlockPos()) && !results.isEmpty() && pending.isEmpty()) {
            IItemHandler target = output.get();
            if (target == null) target = shaft.outputHandler;
            if (remaining > 0) run(shaft, level, miner, results, target);
            for (int step = 0; step < 4 && remaining == 0 && pending.isEmpty(); step++) {
                progress += progress == 0 ? 1 : Mining.advance(level, miner);
                if (progress >= duration) {
                    progress = 0;
                    remaining = 9;
                    run(shaft, level, miner, results, target);
                }
            }
            shaft.setChanged();
        }
        shaft.miningTime = level.hasNeighborSignal(shaft.getBlockPos()) || results.isEmpty()
            ? 0 : (int) Math.max(1, duration - progress);
        if (Math.abs(shaft.lastSyncedMiningTime - (long) shaft.miningTime) >= 4
            || shaft.lastSyncedMaxMiningTime != duration
            || (shaft.lastSyncedMiningTime == 0) != (shaft.miningTime == 0))
            shaft.markNetworkDirty();
        return true;
    }

    private void run(DimensionalMineshaftBlockEntity shaft, ServerLevel level, ItemStack miner,
                     List<com.klikli_dev.occultism.crafting.recipe.result.WeightedRecipeResult> results,
                     IItemHandler output) {
        while (remaining > 0 && pending.isEmpty()) {
            Mining.roll(level, miner, results, pending);
            remaining--;
            flush(shaft, output);
        }
    }

    private void flush(DimensionalMineshaftBlockEntity shaft, IItemHandler output) {
        if (output == null) output = shaft.outputHandler;
        int transfers = 64;
        for (var entries = pending.listIterator(); entries.hasNext() && transfers > 0;) {
            var entry = entries.next();
            long count = entry.count();
            while (count > 0 && transfers-- > 0) {
                int offered = (int) Math.min(count, entry.item().getMaxStackSize());
                ItemStack remainder = ItemHandlerHelper.insertItemStacked(output, entry.item().copyWithCount(offered), false);
                count -= offered - remainder.getCount();
                if (!remainder.isEmpty()) break;
            }
            if (count == entry.count()) continue;
            shaft.setChanged();
            if (count == 0) entries.remove();
            else entries.set(new BigStackStorage.Entry(entry.item(), count));
        }
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        if (progress == 0 && remaining == 0 && pending.isEmpty()) return;
        var state = new CompoundTag();
        state.putLong("progress", progress);
        state.putInt("remaining", remaining);
        ListTag items = new ListTag();
        for (var entry : pending) items.add(BigStackStorage.encode(entry, registries));
        state.put("pending", items);
        tag.put(Abyssal.ID, state);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag state = tag.getCompound(tag.contains(Abyssal.ID, Tag.TAG_COMPOUND) ? Abyssal.ID : Abyssal.LEGACY_ID);
        pool = null;
        progress = Math.max(0, state.getLong("progress"));
        remaining = Math.clamp(state.getInt("remaining"), 0, 9);
        pending.clear();
        for (Tag item : state.getList("pending", Tag.TAG_COMPOUND)) {
            var entry = BigStackStorage.decode((CompoundTag) item, registries);
            if (entry != null) pending.add(entry);
        }
    }

    public void drop(Level level, net.minecraft.core.BlockPos pos) {
        for (var entry : pending) BigStackStorage.drop(level, pos, entry);
        pending.clear();
    }
}
