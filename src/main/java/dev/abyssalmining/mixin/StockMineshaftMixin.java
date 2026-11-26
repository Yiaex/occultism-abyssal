package dev.abyssalmining.mixin;

import com.klikli_dev.occultism.common.blockentity.DimensionalMineshaftBlockEntity;
import dev.abyssalmining.StockMining;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DimensionalMineshaftBlockEntity.class, remap = false)
abstract class StockMineshaftMixin implements StockMining.Access {
    @Unique private StockMining abyssalMining$work;

    @Override public StockMining abyssalMining$state() { return abyssalMining$work; }

    @Invoker("getCurrentHandler")
    abstract IItemHandler abyssalMining$output();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void abyssalMining$tick(CallbackInfo callback) {
        var shaft = (DimensionalMineshaftBlockEntity) (Object) this;
        if (!(shaft.getLevel() instanceof ServerLevel level)) return;
        if (abyssalMining$work == null) abyssalMining$work = new StockMining();
        if (abyssalMining$work.tick(shaft, level, this::abyssalMining$output)) callback.cancel();
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void abyssalMining$save(CompoundTag tag, HolderLookup.Provider lookup, CallbackInfo callback) {
        if (abyssalMining$work != null) abyssalMining$work.save(tag, lookup);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void abyssalMining$load(CompoundTag tag, HolderLookup.Provider lookup, CallbackInfo callback) {
        abyssalMining$work = new StockMining();
        abyssalMining$work.load(tag, lookup);
    }
}
