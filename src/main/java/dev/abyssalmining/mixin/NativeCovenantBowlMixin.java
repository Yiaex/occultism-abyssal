package dev.abyssalmining.mixin;

import com.klikli_dev.occultism.common.blockentity.GoldenSacrificialBowlBlockEntity;
import com.klikli_dev.occultism.crafting.recipe.RitualRecipe;
import dev.abyssalmining.Abyssal;
import dev.abyssalmining.NativeCovenantRitual;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GoldenSacrificialBowlBlockEntity.class, remap = false)
abstract class NativeCovenantBowlMixin {
    @Unique private int abyssalMining$elapsed;
    @Unique private static final String ABYSSAL_ELAPSED = Abyssal.ID + ":elapsed";
    @Unique private static final String LEGACY_ELAPSED = Abyssal.LEGACY_ID + ":elapsed";

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void abyssalMining$waitForChunks(CallbackInfo callback) {
        var bowl = (GoldenSacrificialBowlBlockEntity) (Object) this;
        if (!(bowl.getLevel() instanceof ServerLevel level)) return;
        var current = bowl.getCurrentRitualRecipe();
        if (current != null && current.value().getRitual() instanceof NativeCovenantRitual
            && !NativeCovenantRitual.loaded(level, bowl.getBlockPos())) callback.cancel();
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
        target = "Lcom/klikli_dev/occultism/crafting/recipe/RitualRecipe;getDuration()I", ordinal = 0))
    private int abyssalMining$chaliceProgress(RitualRecipe recipe) {
        if (!(recipe.getRitual() instanceof NativeCovenantRitual ritual)) return recipe.getDuration();
        var bowl = (GoldenSacrificialBowlBlockEntity) (Object) this;
        int target = ritual.durationTicks(bowl.getLevel());
        abyssalMining$elapsed = Math.min(target, abyssalMining$elapsed + 1);
        bowl.setChanged();
        return (int) ((long) abyssalMining$elapsed * recipe.getDuration() / target);
    }

    @Inject(method = "startRitual", at = @At("HEAD"))
    private void abyssalMining$resetTimer(ServerPlayer player, ItemStack item, RecipeHolder<RitualRecipe> recipe,
                                        CallbackInfoReturnable<Boolean> callback) {
        abyssalMining$elapsed = 0;
    }

    @Inject(method = "onLivingDeath", at = @At("HEAD"), cancellable = true)
    private void abyssalMining$delegateSacrifice(LivingDeathEvent event, CallbackInfo callback) {
        var bowl = (GoldenSacrificialBowlBlockEntity) (Object) this;
        var current = bowl.getCurrentRitualRecipe();
        if (current != null && current.value().getRitual() instanceof NativeCovenantRitual) callback.cancel();
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void abyssalMining$saveTimer(CompoundTag tag, HolderLookup.Provider lookup, CallbackInfo callback) {
        var bowl = (GoldenSacrificialBowlBlockEntity) (Object) this;
        var current = bowl.getCurrentRitualRecipe();
        if (current != null && current.value().getRitual() instanceof NativeCovenantRitual)
            tag.putInt(ABYSSAL_ELAPSED, abyssalMining$elapsed);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void abyssalMining$loadTimer(CompoundTag tag, HolderLookup.Provider lookup, CallbackInfo callback) {
        var bowl = (GoldenSacrificialBowlBlockEntity) (Object) this;
        var recipe = bowl.currentRitualRecipeId;
        if (recipe != null && Abyssal.LEGACY_ID.equals(recipe.getNamespace()))
            bowl.currentRitualRecipeId = ResourceLocation.fromNamespaceAndPath(Abyssal.ID, recipe.getPath());
        String elapsedKey = tag.contains(ABYSSAL_ELAPSED) ? ABYSSAL_ELAPSED : LEGACY_ELAPSED;
        abyssalMining$elapsed = Math.clamp(tag.getInt(elapsedKey), 0, 1200000);
        if (!tag.contains(elapsedKey)) return;
        // Upstream checks its recipe before loading the persisted ID on a fresh block entity.
        bowl.consumedIngredients.clear();
        for (Tag item : tag.getList("consumedIngredients", Tag.TAG_COMPOUND))
            ItemStack.parse(lookup, item).ifPresent(bowl.consumedIngredients::add);
        bowl.remainingAdditionalIngredients = null;
    }
}
