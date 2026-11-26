package dev.abyssalmining;

import com.klikli_dev.occultism.common.blockentity.GoldenSacrificialBowlBlockEntity;
import com.klikli_dev.occultism.common.blockentity.SacrificialBowlBlockEntity;
import com.klikli_dev.occultism.common.ritual.CraftRitual;
import com.klikli_dev.occultism.crafting.recipe.RitualRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public final class NativeCovenantRitual extends CraftRitual {
    static final int RADIUS = 9;
    public NativeCovenantRitual(RitualRecipe recipe) {
        super(recipe);
    }

    public static boolean loaded(Level level, BlockPos center) {
        return level.hasChunksAt(center.offset(-RADIUS, 0, -RADIUS), center.offset(RADIUS, 0, RADIUS));
    }

    @Override public boolean isValid(Level level, BlockPos center, GoldenSacrificialBowlBlockEntity bowl,
                                     Player player, ItemStack activationItem, List<Ingredient> remaining) {
        if (recipe.getDuration() <= 0 || !loaded(level, center) || Covenant.legacyInProgress(level, center)) return false;
        if (remaining.isEmpty())
            return recipe.getPentacle() != null && recipe.getActivationItem().test(activationItem)
                && recipe.getPentacle().validate(level, center) != null;
        return super.isValid(level, center, bowl, player, activationItem, remaining);
    }

    @Override public boolean matchesAdditionalIngredients(List<Ingredient> ingredients, List<ItemStack> items) {
        if (items.size() < ingredients.size()) return false;
        int[] assignment = new int[items.size()];
        java.util.Arrays.fill(assignment, -1);
        for (int i = 0; i < ingredients.size(); i++) {
            if (!assign(i, ingredients, items, assignment, new boolean[items.size()])) return false;
        }
        return true;
    }

    private static boolean assign(int ingredient, List<Ingredient> ingredients, List<ItemStack> items,
                                  int[] assignment, boolean[] seen) {
        for (int slot = 0; slot < items.size(); slot++) {
            if (seen[slot] || !ingredients.get(ingredient).test(items.get(slot))) continue;
            seen[slot] = true;
            if (assignment[slot] < 0 || assign(assignment[slot], ingredients, items, assignment, seen)) {
                assignment[slot] = ingredient;
                return true;
            }
        }
        return false;
    }

    @Override public List<SacrificialBowlBlockEntity> getSacrificialBowls(Level level, BlockPos center) {
        var bowls = new ArrayList<SacrificialBowlBlockEntity>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-RADIUS, 0, -RADIUS),
                                                 center.offset(RADIUS, 0, RADIUS))) {
            if (!level.hasChunkAt(pos)) continue;
            if (level.getBlockEntity(pos) instanceof SacrificialBowlBlockEntity bowl
                && !(bowl instanceof GoldenSacrificialBowlBlockEntity)) bowls.add(bowl);
        }
        return bowls;
    }

    @Override public void finish(Level level, BlockPos center, GoldenSacrificialBowlBlockEntity bowl,
                                 ServerPlayer player, ItemStack activationItem) {
        var active = bowl.getCurrentRitualRecipe();
        if (active == null || active.value() != recipe || bowl.currentTime < recipe.getDuration()
            || !bowl.sacrificeFulfilled() || !bowl.remainingAdditionalIngredients.isEmpty()) return;
        super.finish(level, center, bowl, player, activationItem);
    }

    public int durationTicks(Level level) {
        var id = BuiltInRegistries.ITEM.getKey(recipe.getResultItem(level.registryAccess()).getItem());
        int configuredSlot = id.getNamespace().equals(Abyssal.ID) ? Settings.RITUAL_OUTPUTS.indexOf(id.getPath()) : -1;
        int seconds = Math.clamp(recipe.getDuration(), 1, 60000);
        if (configuredSlot < 0 || seconds != Settings.DEFAULT_SECONDS.get(configuredSlot)) return seconds * 20;
        return Settings.DURATIONS.get(configuredSlot).get();
    }
}
