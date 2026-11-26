package dev.abyssalmining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

class RuneBlock extends Block implements EntityBlock {
    RuneBlock(Properties properties) { super(properties); }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return box(0, 0, 0, 16, 1, 16);
    }

    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor,
                                               LevelAccessor level, BlockPos pos, BlockPos other) {
        return side == Direction.DOWN && !canSurvive(state, level, pos)
            ? Blocks.AIR.defaultBlockState() : super.updateShape(state, side, neighbor, level, pos, other);
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Mark(pos, state); }

    @Override public net.minecraft.world.level.material.PushReaction getPistonPushReaction(BlockState state) {
        return net.minecraft.world.level.material.PushReaction.BLOCK;
    }

    static class Mark extends BlockEntity {
        private boolean drawn;

        Mark(BlockPos pos, BlockState state) { this(Abyssal.RUNE_MARK.get(), pos, state); }

        Mark(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

        boolean isDrawn() { return drawn; }

        void markDrawn() {
            drawn = true;
            setChanged();
        }

        @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            drawn = tag.getBoolean("drawnWithCovenantChalk");
        }

        @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.putBoolean("drawnWithCovenantChalk", drawn);
        }
    }

    static final class Anchor extends RuneBlock {
        Anchor(Properties properties) { super(properties); }

        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Covenant(pos, state); }

        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (type != Abyssal.COVENANT.get() || !(level instanceof ServerLevel server)) return null;
            return (world, pos, block, entity) -> ((Covenant) entity).tick(server);
        }

        @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
            if (!state.is(replacement.getBlock()) && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof Covenant covenant) covenant.onRemoved(server);
            super.onRemove(state, level, pos, replacement, moving);
        }
    }
}
