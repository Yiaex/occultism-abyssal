package dev.abyssalmining.mixin;

import com.klikli_dev.occultism.common.block.DimensionalMineshaftBlock;
import dev.abyssalmining.StockMining;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DimensionalMineshaftBlock.class, remap = false)
abstract class StockMineshaftBlockMixin {
    @Inject(method = "onRemove", at = @At("HEAD"))
    private void abyssalMining$drop(BlockState state, Level level, BlockPos pos, BlockState replacement,
                                    boolean moving, CallbackInfo callback) {
        if (!level.isClientSide && !state.is(replacement.getBlock())
            && level.getBlockEntity(pos) instanceof StockMining.Access access && access.abyssalMining$state() != null)
            access.abyssalMining$state().drop(level, pos);
    }
}
