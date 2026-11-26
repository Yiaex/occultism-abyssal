package dev.abyssalmining;

import com.klikli_dev.occultism.Occultism;
import com.klikli_dev.occultism.common.blockentity.DimensionalMineshaftBlockEntity;
import com.klikli_dev.occultism.crafting.recipe.result.WeightedRecipeResult;
import com.klikli_dev.occultism.registry.OccultismRecipes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;

final class Mining {
    private static final ResourceKey<Enchantment> UNUSING = ResourceKey.create(Registries.ENCHANTMENT,
        ResourceLocation.parse("evilcraft:unusing"));

    static int enchantment(ServerLevel level, ItemStack stack, ResourceKey<Enchantment> key) {
        if (!stack.isEnchanted()) return 0;
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key)
            .map(stack::getEnchantmentLevel).orElse(0);
    }

    static boolean protectedByUnusing(ServerLevel level, ItemStack stack) {
        return stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() < 6
            && enchantment(level, stack, UNUSING) > 0;
    }

    static boolean valid(net.minecraft.world.level.Level level, ItemStack stack) {
        return !stack.isEmpty() && level.getRecipeManager().getAllRecipesFor(OccultismRecipes.MINER_TYPE.get())
            .stream().anyMatch(recipe -> recipe.value().getIngredients().getFirst().test(stack));
    }

    private static long recipeGeneration;

    static void reload(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) recipeGeneration++;
    }

    static final class Pool {
        private RecipeManager manager;
        private long generation = -1;
        private List<WeightedRecipeResult> results = List.of();
        private boolean damageSensitive;
        private int damage;

        List<WeightedRecipeResult> results(ServerLevel level, ItemStack stack) {
            RecipeManager currentManager = level.getRecipeManager();
            if (manager == currentManager && generation == recipeGeneration
                && (!damageSensitive || damage == stack.getDamageValue())) return results;
            manager = currentManager;
            generation = recipeGeneration;
            var current = manager.getAllRecipesFor(OccultismRecipes.MINER_TYPE.get());
            damage = stack.getDamageValue();
            damageSensitive = current.stream().anyMatch(r -> !r.value().getIngredients().getFirst().isSimple());
            results = current.stream().map(recipe -> recipe.value())
                .filter(r -> r.getIngredients().getFirst().test(stack))
                .map(recipe -> recipe.getWeightedResult()).toList();
            return results;
        }
    }

    static long duration(ItemStack stack) {
        return Math.max(1L, DimensionalMineshaftBlockEntity.getMaxMiningTime(stack));
    }

    static int advance(ServerLevel level, ItemStack miner) {
        int efficiency = Occultism.SERVER_CONFIG.itemSettings.minerEfficiency.getAsBoolean()
            ? enchantment(level, miner, Enchantments.EFFICIENCY) : 0;
        if (efficiency <= 0) return 1;
        return 1 + Math.min(level.random.nextInt(efficiency + 1), level.random.nextInt(efficiency + 1));
    }

    static void roll(ServerLevel level, ItemStack miner, List<WeightedRecipeResult> results,
                     List<BigStackStorage.Entry> batch) {
        var config = Occultism.SERVER_CONFIG.itemSettings;
        int fortune = config.minerFortune.getAsBoolean() ? enchantment(level, miner, Enchantments.FORTUNE) : 0;
        if (fortune > 0) {
            int bound = fortune + 1;
            fortune = Math.min(level.random.nextInt(bound), Math.min(level.random.nextInt(bound), level.random.nextInt(bound)));
        }
        int silk = config.minerSilk.getAsBoolean() ? enchantment(level, miner, Enchantments.SILK_TOUCH) : 0;
        int rolls = DimensionalMineshaftBlockEntity.getRollsPerOperation(miner) + fortune;
        for (int i = 0; i < rolls; i++) {
            var result = WeightedRandom.getRandomItem(level.random, results);
            int multiplier = silk > 0 ? 1 + level.random.nextInt(silk + 1) : 1;
            result.ifPresent(entry -> BigStackStorage.add(batch, entry.getStack(), (long) entry.getStack().getCount() * multiplier));
        }

        if (miner.is(Abyssal.MINER.get())) {
            miner.set(DataComponents.UNBREAKABLE, Abyssal.MINER.get().components().get(DataComponents.UNBREAKABLE));
            return;
        }
        miner.hurtAndBreak(1, level, null, item -> {});
        if (!miner.isEmpty() && miner.isDamageableItem() && config.minerOutputBeforeBreak.getAsBoolean()
            && miner.getDamageValue() == miner.getMaxDamage() - 1) {
            BigStackStorage.add(batch, miner, 1);
            miner.shrink(1);
        }
    }
}
