package dev.abyssalmining;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

final class MachineBlock extends BaseEntityBlock {
    final boolean battlefield;

    MachineBlock(boolean battlefield) {
        super(Properties.of().strength(4.5f, 8).requiresCorrectToolForDrops().lightLevel(s -> 3));
        this.battlefield = battlefield;
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(properties -> new MachineBlock(battlefield));
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Machine(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel server
            ? createTickerHelper(type, Abyssal.MACHINE.get(), (ignored, pos, block, machine) -> machine.tick(server))
            : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof Machine machine) {
            player.openMenu(machine, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!level.isClientSide && !state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof Machine machine) {
            machine.dropInventory();
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override
    public net.minecraft.world.level.material.PushReaction getPistonPushReaction(BlockState state) {
        return net.minecraft.world.level.material.PushReaction.BLOCK;
    }
}
