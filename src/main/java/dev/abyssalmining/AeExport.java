package dev.abyssalmining;

import appeng.api.AECapabilities;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.EnumSet;

final class AeExport implements Machine.Export, IInWorldGridNodeHost, IActionHost {
    private final Machine machine;
    private final IActionSource source = IActionSource.ofMachine(this);
    private final IManagedGridNode node;

    private AeExport(Machine machine) {
        this.machine = machine;
        node = GridHelper.createManagedNode(this, (owner, changed) -> owner.machine.setChanged())
            .setInWorldNode(true)
            .setFlags(GridFlags.REQUIRE_CHANNEL)
            .setIdlePowerUsage(1)
            .setVisualRepresentation(machine.getBlockState().getBlock())
            .setExposedOnSides(EnumSet.allOf(Direction.class));
    }

    static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, Abyssal.MACHINE.get(),
            (machine, unused) -> machine.export() instanceof AeExport export ? export : null);
    }

    static Machine.Export create(Machine machine) { return new AeExport(machine); }

    @Override public IGridNode getGridNode(Direction side) { return node.getNode(); }
    @Override public IGridNode getActionableNode() { return node.getNode(); }

    @Override public void start(ServerLevel level, CompoundTag saved) {
        node.loadFromNBT(saved);
        node.create(level, machine.getBlockPos());
    }

    @Override public CompoundTag save() {
        var tag = new CompoundTag();
        node.saveToNBT(tag);
        return tag;
    }

    @Override public void close() { node.destroy(); }

    @Override public boolean online() { return node.isActive(); }

    @Override public long insert(BigStackStorage.Entry entry) {
        if (!online()) return 0;
        var grid = node.getGrid();
        if (grid == null) return 0;
        return StorageHelper.poweredInsert(grid.getEnergyService(), grid.getStorageService().getInventory(),
            AEItemKey.of(entry.item()), entry.count(), source);
    }
}
