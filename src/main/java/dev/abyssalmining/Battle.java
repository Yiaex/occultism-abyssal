package dev.abyssalmining;

import com.klikli_dev.occultism.Occultism;
import com.klikli_dev.occultism.common.entity.possessed.PossessedMob;
import com.klikli_dev.occultism.common.item.tool.SoulGemItem;
import com.klikli_dev.occultism.registry.OccultismDataComponents;
import com.klikli_dev.occultism.registry.OccultismItems;
import com.klikli_dev.occultism.registry.OccultismTags;
import com.klikli_dev.occultism.util.EntityUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.List;

final class Battle {
    private static final double DIAMOND_SWORD_DAMAGE = 7.0;

    record Target(LivingEntity combat, LivingEntity loot, int health, boolean possessed, boolean boss) {}
    record Timing(int hits, int interval) {
        long ticks() { return (long) hits * interval; }
    }

    static EntityType<?> type(ItemStack source) {
        if (source.isEmpty()) return null;
        var data = source.getOrDefault(DataComponents.ENTITY_DATA, CustomData.EMPTY);
        EntityType<?> type = source.getItem() instanceof SpawnEggItem egg ? egg.getType(source)
            : EntityUtil.entityTypeFromNbt(data.copyTag());
        if (type == null || type == EntityType.PLAYER) return null;
        if (source.is(OccultismItems.FRAGILE_SOUL_GEM_ITEM))
            return type.is(OccultismTags.Entities.FRAGILE_SOUL_GEM_DENY_LIST) ? null : type;
        if (source.is(OccultismItems.SOUL_GEM_ITEM))
            return type.is(OccultismTags.Entities.SOUL_GEM_DENY_LIST) ? null : type;
        if (source.is(OccultismItems.TRINITY_GEM_ITEM))
            return type.is(OccultismTags.Entities.TRINITY_GEM_DENY_LIST) ? null : type;
        return type.is(OccultismTags.Entities.TRINITY_GEM_DENY_LIST) ? null : type;
    }

    static Target target(ServerLevel level, ItemStack source) {
        EntityType<?> type = type(source);
        if (type == null || !(type.create(level) instanceof LivingEntity combat)) return null;
        LivingEntity loot = combat;
        boolean possessed = combat instanceof PossessedMob && source.is(OccultismItems.TRINITY_GEM_ITEM);
        if (combat instanceof PossessedMob spirit && spirit.basedMob() != null
            && spirit.basedMob().create(level) instanceof LivingEntity base) {
            loot = base;
            if (!possessed) combat = base;
        }
        int health = (int) combat.getMaxHealth();
        var entityData = source.getOrDefault(DataComponents.ENTITY_DATA, CustomData.EMPTY).copyTag();
        var attrs = entityData.getList("attributes", Tag.TAG_COMPOUND);
        for (int i = 0; i < attrs.size(); i++) {
            var attribute = attrs.getCompound(i);
            if ("minecraft:generic.max_health".equals(attribute.getString("id"))) {
                health = (int) attribute.getDouble("base");
                break;
            }
        }
        return new Target(combat, loot, Math.clamp(health, 1, 1024), possessed,
            combat.getType().is(Tags.EntityTypes.BOSSES));
    }

    static boolean weapon(ItemStack stack) {
        return !stack.isEmpty() && stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
            .modifiers().stream().anyMatch(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE)
                && entry.slot().test(EquipmentSlot.MAINHAND) && entry.modifier().amount() > 0);
    }

    static boolean fuel(ItemStack stack) {
        return !stack.isEmpty() && stack.getOrDefault(OccultismDataComponents.SOUL_VALUE, 0) > 0;
    }

    static int requiredFuel(ItemStack source, ItemStack fuel, Target target) {
        if (source.has(OccultismDataComponents.SOUL_VALUE)) return 0;
        int value = fuel.getOrDefault(OccultismDataComponents.SOUL_VALUE, 0);
        return value <= 0 ? Integer.MAX_VALUE : 1 + target.health / value;
    }

    static Timing timing(ServerLevel level, Target target, ItemStack weapon) {
        var modifiers = weapon.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double speed = mainHandAttribute(modifiers, Attributes.ATTACK_SPEED, 4);
        double damage = mainHandAttribute(modifiers, Attributes.ATTACK_DAMAGE, 1);
        EntityType<?> type = target.combat.getType();
        damage += Mining.enchantment(level, weapon, Enchantments.SHARPNESS);
        if (type.is(EntityTypeTags.SENSITIVE_TO_SMITE))
            damage += 2.5 * Mining.enchantment(level, weapon, Enchantments.SMITE);
        if (type.is(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS))
            damage += 2.5 * Mining.enchantment(level, weapon, Enchantments.BANE_OF_ARTHROPODS);
        if (type.is(EntityTypeTags.SENSITIVE_TO_IMPALING))
            damage += 2.5 * Mining.enchantment(level, weapon, Enchantments.IMPALING);
        if (weapon.is(OccultismTags.Items.TOOLS_KNIFE_IESNIUM)
            && (type.is(OccultismTags.Entities.HEALED_BY_DEMONS_DREAM_FRUIT) || target.boss)) damage *= 3;
        if (speed <= 0 || damage <= 0 || !Double.isFinite(speed) || !Double.isFinite(damage)) return null;
        long health = (long) target.health * Occultism.SERVER_CONFIG.itemSettings.butcherLifeMultiplier.getAsInt();
        int hits = Math.clamp((int) (health / damage), 1, 65536);
        double ticksPerHit = (40 / speed) * (DIAMOND_SWORD_DAMAGE / damage);
        return new Timing(hits, Math.clamp((int) ticksPerHit, 1, 12000));
    }

    private static double mainHandAttribute(ItemAttributeModifiers modifiers, Holder<Attribute> attribute, double base) {
        double added = base;
        double multipliedBase = 0;
        double multipliedTotal = 1;
        for (var entry : modifiers.modifiers()) {
            if (!entry.attribute().equals(attribute) || !entry.slot().test(EquipmentSlot.MAINHAND)) continue;
            double amount = entry.modifier().amount();
            switch (entry.modifier().operation()) {
                case ADD_VALUE -> added += amount;
                case ADD_MULTIPLIED_BASE -> multipliedBase += amount;
                case ADD_MULTIPLIED_TOTAL -> multipliedTotal *= 1 + amount;
            }
        }
        return (added + added * multipliedBase) * multipliedTotal;
    }

    static boolean hit(ServerLevel level, ItemStack weapon) {
        if (weapon.isEmpty() || Mining.protectedByUnusing(level, weapon)) return false;
        if (level.random.nextFloat() < Occultism.SERVER_CONFIG.itemSettings.butcherHurtChance.getAsDouble())
            weapon.hurtAndBreak(1, level, null, item -> {});
        return true;
    }

    static int kill(ServerLevel level, BlockPos pos, Target target, ItemStack source, ItemStack fuel,
                    ItemStack weapon, int fuelCost, int multiplier, List<BigStackStorage.Entry> batch) {
        fuel.shrink(fuelCost);
        if (level.random.nextFloat() < source.getOrDefault(OccultismDataComponents.FAIL_CHANCE, 0F)) return 0;

        int rolls = Math.clamp(source.getOrDefault(OccultismDataComponents.ROLLS_PER_OPERATION, 1), 1, 1024);
        if (target.boss) rolls = 1;
        if (target.possessed) rolls = Math.max(1, rolls / 3);
        int luck = Math.clamp(rolls, 1, 16); // Occultism 1.227.0 derives effective luck from the roll count.
        rolls += luck * luck / 100;
        if (level.random.nextInt(100) < luck * luck % 100) rolls++;
        if (level.random.nextFloat() < source.getOrDefault(OccultismDataComponents.CONSUME_CHANCE, 0F) / luck)
            source.shrink(1);

        LivingEntity victim = target.loot;
        var player = FakePlayerFactory.getMinecraft(level);
        var originalHeld = player.getMainHandItem();
        var originalPosition = player.position();
        var origin = pos.getCenter();
        victim.moveTo(origin);
        player.moveTo(origin);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        try {
            var damage = player.damageSources().playerAttack(player);
            var params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, victim)
                .withParameter(LootContextParams.ORIGIN, origin)
                .withParameter(LootContextParams.DAMAGE_SOURCE, damage)
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                .withParameter(LootContextParams.ATTACKING_ENTITY, player)
                .withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, player)
                .withParameter(LootContextParams.TOOL, weapon)
                .withLuck(luck).create(LootContextParamSets.ENTITY);
            var registry = level.getServer().reloadableRegistries();
            var entityId = BuiltInRegistries.ENTITY_TYPE.getKey(target.combat.getType());
            var custom = ResourceLocation.fromNamespaceAndPath("occultism",
                "battlefield/" + entityId.getNamespace() + "/" + entityId.getPath());
            var table = registry.getLootTable(ResourceKey.create(Registries.LOOT_TABLE, custom));
            if (table == LootTable.EMPTY) table = registry.getLootTable(target.combat.getLootTable());
            long bonusLeft = Settings.LOOT_QUANTITY.get();
            for (int roll = 0; roll < rolls; roll++) {
                List<ItemEntity> drops = new ArrayList<>();
                for (ItemStack stack : table.getRandomItems(params))
                    drops.add(new ItemEntity(level, origin.x, origin.y, origin.z, stack));
                var event = new LivingDropsEvent(victim, damage, drops, true);
                if (NeoForge.EVENT_BUS.post(event).isCanceled()) continue;
                for (ItemEntity drop : event.getDrops()) {
                    ItemStack stack = drop.getItem();
                    long extra = multiply(stack) ? Math.min(bonusLeft, (long) stack.getCount() * (multiplier - 1)) : 0;
                    bonusLeft -= extra;
                    BigStackStorage.add(batch, stack, stack.getCount() + extra);
                }
            }
            return Math.max(0, victim.getExperienceReward(level, player));
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, originalHeld);
            player.moveTo(originalPosition);
        }
    }

    private static boolean weaponLoot(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem
            || stack.is(Items.TRIDENT) || stack.is(Items.SHIELD)
            || stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()
                .stream().anyMatch(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE));
    }

    private static boolean multiply(ItemStack stack) {
        return !stack.isEmpty() && !stack.is(Items.EXPERIENCE_BOTTLE)
            && !(stack.getItem() instanceof SpawnEggItem || stack.getItem() instanceof SoulGemItem)
            && !fuel(stack) && !stack.has(DataComponents.ENTITY_DATA)
            && !weaponLoot(stack)
            && !Settings.LOOT_BLACKLIST.get().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
}
