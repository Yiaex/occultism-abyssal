package dev.abyssalmining;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

final class MachineMenu extends AbstractContainerMenu {
    static final int WORK_DATA = BigStackStorage.SLOTS;
    static final int UPGRADES = WORK_DATA + Machine.SOURCES * 3;
    static final int EXPORT_STATE = UPGRADES + 3;
    static final int OUTPUT_HIGH = EXPORT_STATE + 1;
    static final int DATA_SIZE = OUTPUT_HIGH + BigStackStorage.SLOTS;
    static final int SIDE_X = 180;
    private final Machine machine;
    private final BlockPos pos;
    final boolean battlefield;
    private final int deviceSlots;
    private final ContainerData data;

    MachineMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, (Machine) inventory.player.level().getBlockEntity(buf.readBlockPos()));
    }

    MachineMenu(int id, Inventory inventory, Machine machine) {
        super(Abyssal.MENU.get(), id);
        this.machine = machine;
        this.pos = machine.getBlockPos();
        this.battlefield = machine.battlefield;
        boolean server = !inventory.player.level().isClientSide();
        var display = new SimpleContainer(BigStackStorage.SLOTS);
        for (int i = 0; i < BigStackStorage.SLOTS; i++) {
            int index = i;
            addSlot(new Slot(display, index, 8 + i % 9 * 18, 28 + i / 9 * 18) {
                @Override public ItemStack getItem() { return server ? machine.output.read(index) : super.getItem(); }
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
            });
        }
        for (int i = 0; i < Machine.SOURCES; i++) addDevice(i, SIDE_X + i % 3 * 18, 28 + i / 3 * 22);
        if (battlefield) {
            addDevice(Machine.WEAPON, SIDE_X + 4, 96);
            addDevice(Machine.FUEL, SIDE_X + 34, 96);
        }
        for (int i = 0; i < 3; i++) {
            addDevice(Machine.STACK + i, SIDE_X + i * 18, 120);
            addDevice(Machine.PARALLEL + i, SIDE_X + i * 18, 148);
            if (battlefield) addDevice(Machine.LOOT + i, SIDE_X + i * 18, 176);
        }
        if (battlefield) addDevice(Machine.MAINTENANCE, SIDE_X + 4, 205);
        addDevice(Machine.ME, SIDE_X + 36, battlefield ? 205 : 201);
        deviceSlots = slots.size();
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 146 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 204));
        data = server ? new ContainerData() {
            @Override public int get(int index) {
                if (index < WORK_DATA) return (short) machine.output.count(index);
                if (index >= OUTPUT_HIGH) return (short) (machine.output.count(index - OUTPUT_HIGH) >>> 16);
                if (index < UPGRADES) {
                    int offset = index - WORK_DATA;
                    return switch (offset % 3) {
                        case 0 -> machine.progress(offset / 3);
                        case 1 -> machine.parallel(offset / 3);
                        default -> machine.status(offset / 3);
                    };
                }
                if (index == EXPORT_STATE) return machine.export() != null && machine.export().online() ? 1 : 0;
                return machine.cards(Machine.STACK + 3 * (index - UPGRADES));
            }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return DATA_SIZE; }
        } : new SimpleContainerData(DATA_SIZE);
        addDataSlots(data);
    }

    int data(int index) { return data.get(index); }

    int outputCount(int slot) {
        return joinOutputCount(data.get(slot), data.get(OUTPUT_HIGH + slot));
    }

    // Minecraft 1.21.1 sends each menu data entry as a signed short.
    static int joinOutputCount(int low, int high) {
        return (high & 0xffff) << 16 | (low & 0xffff);
    }

    private void addDevice(int slot, int x, int y) {
        addSlot(new SlotItemHandler(machine.input, slot, x, y));
    }

    @Override public boolean stillValid(Player player) {
        return !machine.isRemoved() && player.level().getBlockEntity(pos) == machine
            && player.distanceToSqr(pos.getCenter()) <= 64;
    }

    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.index >= BigStackStorage.SLOTS;
    }

    @Override
    public void clicked(int index, int button, ClickType type, Player player) {
        if (player.level().isClientSide()) return;
        if (!stillValid(player)) return;
        boolean output = index >= 0 && index < BigStackStorage.SLOTS;
        if (type == ClickType.PICKUP_ALL && !getCarried().isEmpty() && !output) {
            super.clicked(index, button, type, player);
            collect(button);
            return;
        }
        if (!output) {
            super.clicked(index, button, type, player);
            return;
        }
        ItemStack visible = machine.output.read(index);
        if (visible.isEmpty() && type != ClickType.PICKUP_ALL) return;
        switch (type) {
            case QUICK_MOVE -> quickMoveStack(player, index);
            case PICKUP -> {
                if (button != 0 && button != 1) return;
                ItemStack carried = getCarried();
                if (carried.isEmpty()) {
                    int amount = button == 1 ? (visible.getCount() + 1) / 2 : visible.getCount();
                    setCarried(machine.extract(index, amount, false));
                } else if (ItemStack.isSameItemSameComponents(carried, visible)) {
                    int count = button == 1 ? Math.min(1, carried.getMaxStackSize() - carried.getCount())
                        : carried.getMaxStackSize() - carried.getCount();
                    carried.grow(machine.extract(index, count, false).getCount());
                }
            }
            case SWAP -> {
                if (button < 0 || (button > 8 && button != 40)) return;
                Inventory inventory = player.getInventory();
                ItemStack held = inventory.getItem(button);
                if (held.isEmpty()) inventory.setItem(button, machine.extract(index, visible.getCount(), false));
                else if (ItemStack.isSameItemSameComponents(held, visible)) {
                    held.grow(machine.extract(index, held.getMaxStackSize() - held.getCount(), false).getCount());
                    inventory.setChanged();
                }
            }
            case PICKUP_ALL -> collect(button);
            case THROW -> {
                if (getCarried().isEmpty() && (button == 0 || button == 1))
                    player.drop(machine.extract(index, button == 0 ? 1 : visible.getMaxStackSize(), false), true);
            }
            case CLONE -> {
                if (player.getAbilities().instabuild && getCarried().isEmpty())
                    setCarried(visible.copyWithCount(visible.getMaxStackSize()));
            }
            default -> {}
        }
    }

    private void collect(int button) {
        ItemStack carried = getCarried();
        if (carried.isEmpty()) return;
        for (int i = 0; i < BigStackStorage.SLOTS && carried.getCount() < carried.getMaxStackSize(); i++) {
            int slot = button == 0 ? i : BigStackStorage.SLOTS - i - 1;
            if (ItemStack.isSameItemSameComponents(carried, machine.output.read(slot)))
                carried.grow(machine.extract(slot, carried.getMaxStackSize() - carried.getCount(), false).getCount());
        }
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (player.level().isClientSide() || !stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = getSlot(index);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack saved = stack.copy();
        if (index < BigStackStorage.SLOTS) {
            if (!moveItemStackTo(stack, deviceSlots, slots.size(), true)) return ItemStack.EMPTY;
            machine.extract(index, saved.getCount() - stack.getCount(), false);
            // AbstractContainerMenu's vanilla shift loop retries while a return value is nonempty.
            return ItemStack.EMPTY;
        }

        boolean moved = index < deviceSlots
            ? moveItemStackTo(stack, deviceSlots, slots.size(), true)
            : moveItemStackTo(stack, BigStackStorage.SLOTS, deviceSlots, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return saved;
    }
}
