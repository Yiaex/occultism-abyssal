package dev.abyssalmining;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ItemView;
import snownee.jade.api.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@WailaPlugin
public final class AbyssalJade implements IWailaPlugin {
    private static final ResourceLocation STORAGE = Abyssal.id("warehouse");
    private static final String COUNTS = "counts";

    @Override public void register(IWailaCommonRegistration registration) {
        registration.registerItemStorage(new Storage(), Machine.class);
    }

    @Override public void registerClient(IWailaClientRegistration registration) {
        registration.registerItemStorageClient(new StorageDisplay());
    }

    private static final class Storage implements IServerExtensionProvider<ItemStack> {
        @Override public ResourceLocation getUid() { return STORAGE; }
        @Override public int getDefaultPriority() { return -100; }

        @Override public List<ViewGroup<ItemStack>> getGroups(Accessor<?> accessor) {
            if (!(accessor instanceof BlockAccessor block) || !(block.getBlockEntity() instanceof Machine machine)) return null;
            return groups(machine, accessor.showDetails());
        }
    }

    static List<ViewGroup<ItemStack>> groups(Machine machine, boolean details) {
        int limit = details ? BigStackStorage.SLOTS + Machine.SOURCES : 9;
        var items = new ArrayList<ItemStack>();
        var counts = new ArrayList<Long>();
        for (int slot = 0; slot < BigStackStorage.SLOTS && items.size() < limit; slot++) {
            long count = machine.output.count(slot);
            if (count > 0) {
                items.add(machine.output.read(slot).copyWithCount(1));
                counts.add(count);
            }
        }
        for (int slot = 0; slot < Machine.SOURCES && items.size() < limit; slot++) {
            var stack = machine.input.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                items.add(stack.copyWithCount(1));
                counts.add((long) stack.getCount());
            }
        }
        var data = new CompoundTag();
        data.putLongArray(COUNTS, counts.stream().mapToLong(Long::longValue).toArray());
        return List.of(new ViewGroup<>(items, Optional.empty(), Optional.of(data)));
    }

    private static final class StorageDisplay implements IClientExtensionProvider<ItemStack, ItemView> {
        @Override public ResourceLocation getUid() { return STORAGE; }

        @Override public List<ClientViewGroup<ItemView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<ItemStack>> groups) {
            return clientGroups(groups);
        }
    }

    static List<ClientViewGroup<ItemView>> clientGroups(List<ViewGroup<ItemStack>> groups) {
        return ClientViewGroup.map(groups, ItemView::new, (source, target) -> {
            long[] counts = source.getExtraData().getLongArray(COUNTS);
            for (int i = 0; i < counts.length && i < target.views.size(); i++)
                if (counts[i] > 1) target.views.get(i).amountText(Long.toString(counts[i]));
        });
    }
}
