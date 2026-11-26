package dev.abyssalmining;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static dev.abyssalmining.AbyssalGameTests.check;
import static dev.abyssalmining.AbyssalGameTests.state;
import static dev.abyssalmining.AbyssalGameTests.total;

final class AeNetworkGameTest {
    private static final BlockPos MACHINE = new BlockPos(2, 2, 2);

    static void checkNetwork(GameTestHelper test) {
        test.setBlock(MACHINE, Abyssal.MINE.get());
        Machine machine = test.getBlockEntity(MACHINE);
        var previous = state(test, machine);
        previous.getCompound("input").putInt("Size", 20);
        machine.loadWithComponents(previous, test.getLevel().registryAccess());
        check(test, machine.input.getSlots() == 21, "Old machine inventory did not gain an ME slot");
        ItemStack upgrade = new ItemStack(Abyssal.ME.get());
        check(test, machine.input.insertItem(Machine.ME, upgrade, false).isEmpty(), "Upgrade rejected");
        test.setBlock(MACHINE.east(), appeng.core.definitions.AEBlocks.DRIVE.block());
        test.setBlock(MACHINE.east(2), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        BlockPos arenaPos = MACHINE.east().south();
        test.setBlock(arenaPos, Abyssal.ARENA.get());
        Machine arena = test.getBlockEntity(arenaPos);
        arena.input.setStackInSlot(Machine.ME, upgrade.copy());
        arena.output.insert(new BigStackStorage.Entry(new ItemStack(Items.GOLD_INGOT), 257), 2);
        appeng.blockentity.storage.DriveBlockEntity drive = test.getBlockEntity(MACHINE.east());
        drive.getInternalInventory().setItemDirect(0, appeng.core.definitions.AEItems.ITEM_CELL_1K.stack());
        machine.output.insert(new BigStackStorage.Entry(new ItemStack(Items.IRON_INGOT), 513), 2);
        var copper = appeng.api.stacks.AEItemKey.of(Items.COPPER_INGOT);
        test.startSequence().thenWaitUntil(() -> {
            var cell = drive.getOriginalCellInventory(0);
            check(test, cell != null && cell.getAvailableStacks().get(appeng.api.stacks.AEItemKey.of(Items.IRON_INGOT)) == 513
                && cell.getAvailableStacks().get(appeng.api.stacks.AEItemKey.of(Items.GOLD_INGOT)) == 257
                && total(machine) == 0 && total(arena) == 0, "ME drive did not receive both machines' output exactly once");
        }).thenExecute(() -> {
            machine.output.insert(new BigStackStorage.Entry(new ItemStack(Items.COPPER_INGOT), 63), 0);
            machine.tick(test.getLevel());
            check(test, total(machine) == 63 && drive.getOriginalCellInventory(0).getAvailableStacks().get(copper) == 0,
                "ME exported twice within a batch interval");
        }).thenWaitUntil(() -> check(test, total(machine) == 0
            && drive.getOriginalCellInventory(0).getAvailableStacks().get(copper) == 63,
            "ME did not deliver the next batch")).thenSucceed();
    }
}
