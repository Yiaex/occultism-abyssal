package dev.abyssalmining;

import com.klikli_dev.occultism.common.blockentity.GoldenSacrificialBowlBlockEntity;
import com.klikli_dev.occultism.common.blockentity.SacrificialBowlBlockEntity;
import com.klikli_dev.occultism.registry.OccultismBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class Covenant extends RuneBlock.Mark {
    enum State { IDLE, WAITING_SACRIFICE, RUNNING, INTERRUPTED, COMPLETED, CANCELLED }
    enum Structure { UNLOADED, INVALID, VALID }
    static final BlockPos FOCUS = new BlockPos(0, 0, -1);
    static final List<BlockPos> BOWLS = List.of(
        offset(0,-5), offset(4,-4), offset(5,0), offset(4,4),
        offset(0,5), offset(-4,4), offset(-5,0), offset(-4,-4));
    static final List<BlockPos> RUNES = List.of(
        offset(-1,0), offset(1,0), offset(0,1), offset(0,-2), offset(0,-3), offset(0,-4),
        offset(0,2), offset(0,3), offset(0,4),
        offset(-2,0), offset(-3,0), offset(-4,0), offset(2,0), offset(3,0), offset(4,0),
        offset(-1,-1), offset(-2,-2), offset(-3,-3), offset(1,-1), offset(2,-2), offset(3,-3),
        offset(-1,1), offset(-2,2), offset(-3,3), offset(1,1), offset(2,2), offset(3,3));

    private State state = State.IDLE;
    private final List<ItemStack> escrow = new ArrayList<>();
    private ItemStack result = ItemStack.EMPTY;
    private ResourceLocation recipeId, chaliceId;
    private UUID ritualId, owner, sacrifice;
    private int remaining;

    Covenant(BlockPos pos, BlockState block) {
        super(Abyssal.COVENANT.get(), pos, block);
    }

    private static BlockPos offset(int x, int z) { return new BlockPos(x, 0, z); }
    BlockPos center() { return worldPosition.subtract(FOCUS); }
    private boolean active() { return state == State.RUNNING || state == State.WAITING_SACRIFICE; }

    private static boolean chalice(BlockState block) {
        return block.is(OccultismBlocks.ELDRITCH_CHALICE.get()) || block.is(OccultismBlocks.CELESTIAL_CHALICE.get());
    }

    private Structure structure(ServerLevel level) {
        BlockPos center = center();
        if (!level.hasChunksAt(center.offset(-5, 0, -5), center.offset(5, 0, 5))) return Structure.UNLOADED;
        BlockState block = level.getBlockState(center);
        if (!chalice(block) || !isDrawn() || !level.getBlockState(worldPosition).is(Abyssal.ANCHOR.get())) return Structure.INVALID;
        if (active() && !BuiltInRegistries.BLOCK.getKey(block.getBlock()).equals(chaliceId)) return Structure.INVALID;
        if (!(level.getBlockEntity(center) instanceof GoldenSacrificialBowlBlockEntity bowl)
            || bowl.getCurrentRitualRecipe() != null) return Structure.INVALID;
        for (BlockPos offset : BOWLS) {
            BlockPos pos = center.offset(offset);
            if (!level.getBlockState(pos).is(OccultismBlocks.SACRIFICIAL_BOWL.get())
                || !(level.getBlockEntity(pos) instanceof SacrificialBowlBlockEntity)) return Structure.INVALID;
        }
        for (BlockPos offset : RUNES) {
            BlockPos pos = center.offset(offset);
            if (!level.getBlockState(pos).is(Abyssal.RUNE.get())
                || !(level.getBlockEntity(pos) instanceof RuneBlock.Mark mark) || !mark.isDrawn()) return Structure.INVALID;
        }
        return Structure.VALID;
    }

    void tick(ServerLevel level) {
        if (state == State.INTERRUPTED) {
            cancel();
            return;
        }
        if (!active()) return;
        Structure structure = structure(level);
        if (structure == Structure.UNLOADED) return;
        if (structure == Structure.INVALID) {
            interrupt();
            return;
        }
        if (level.getGameTime() % 20 == 0) {
            Vec3 p = center().getCenter();
            level.sendParticles(state == State.RUNNING ? ParticleTypes.PORTAL : ParticleTypes.SOUL,
                p.x, p.y, p.z, 10, 0.35, 0.3, 0.35, 0.03);
        }
        if (state == State.RUNNING) {
            if (--remaining <= 0) {
                escrow.clear();
                state = State.COMPLETED;
            }
            setChanged();
        }
    }

    private void interrupt() {
        if (!active()) return;
        state = State.INTERRUPTED;
        setChanged();
    }

    private void cancel() {
        result = ItemStack.EMPTY;
        state = State.CANCELLED;
        setChanged();
    }

    void onRemoved(ServerLevel level) {
        for (ItemStack stack : escrow) drop(level, stack);
        escrow.clear();
        if (state == State.COMPLETED && !result.isEmpty()) drop(level, result);
        result = ItemStack.EMPTY;
        state = State.CANCELLED;
    }

    private void drop(ServerLevel level, ItemStack stack) {
        net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
    }

    private void interact(Player player, boolean atCenter) {
        if (active()) {
            if (!atCenter && player.isShiftKeyDown() && (player.getUUID().equals(owner) || player.hasPermissions(2))) {
                interrupt();
                cancel();
                message(player, "cancelled");
            } else if (state == State.WAITING_SACRIFICE) {
                message(player, "waiting", Settings.SACRIFICE_RADIUS.get());
            } else message(player, "running", (remaining + 19) / 20);
            return;
        }
        if (state == State.INTERRUPTED) cancel();
        if (state == State.COMPLETED && !result.isEmpty()) {
            ItemStack award = result;
            result = ItemStack.EMPTY;
            setChanged();
            give(player, award);
            message(player, "completed");
            return;
        }
        if (state == State.CANCELLED && !escrow.isEmpty()) {
            List<ItemStack> refund = List.copyOf(escrow);
            escrow.clear();
            setChanged();
            refund.forEach(stack -> give(player, stack));
            message(player, "refunded");
            return;
        }
        message(player, "native_start");
    }

    static boolean legacyInProgress(Level level, BlockPos center) {
        return level.getBlockEntity(center.offset(FOCUS)) instanceof Covenant legacy && legacy.active();
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false);
    }

    private static void message(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("ritual." + Abyssal.ID + "." + key, args), true);
    }

    static void interact(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        var level = event.getLevel();
        BlockPos clicked = event.getPos();
        ItemStack stack = event.getItemStack();
        boolean main = event.getHand() == InteractionHand.MAIN_HAND;
        if (!main || !stack.isEmpty()) return;
        BlockState block = level.getBlockState(clicked);
        boolean atCenter = player.isShiftKeyDown() && chalice(block);
        BlockPos anchor = atCenter ? clicked.offset(FOCUS) : clicked;
        if (!level.getBlockState(anchor).is(Abyssal.ANCHOR.get())) return;
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        event.setCanceled(true);
        if (level instanceof ServerLevel server && server.mayInteract(player, clicked)
            && level.getBlockEntity(anchor) instanceof Covenant covenant) covenant.interact(player, atCenter);
    }

    static void breakPart(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        BlockPos pos = event.getPos();
        var offsets = new ArrayList<>(BOWLS);
        offsets.addAll(RUNES);
        offsets.add(BlockPos.ZERO);
        for (BlockPos offset : offsets) {
            BlockPos focus = pos.subtract(offset).offset(FOCUS);
            if (server.hasChunkAt(focus) && server.getBlockEntity(focus) instanceof Covenant covenant)
                covenant.interrupt();
        }
    }

    static void sacrifice(LivingDeathEvent event) {
        var victim = event.getEntity();
        String used = Abyssal.ID + ":sacrifice_used";
        if (victim.getType() != EntityType.WITHER || !(victim.level() instanceof ServerLevel level)
            || !(event.getSource().getEntity() instanceof Player player)
            || player instanceof net.neoforged.neoforge.common.util.FakePlayer) return;
        if (victim.getPersistentData().getBoolean(used)
            || victim.getPersistentData().getBoolean(Abyssal.LEGACY_ID + ":sacrifice_used")) return;
        int radius = Settings.SACRIFICE_RADIUS.get();
        var origin = victim.position();
        BlockPos pos = victim.blockPosition();
        net.minecraft.world.level.block.entity.BlockEntity best = null;
        long bestPosition = 0;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (BlockPos center : BlockPos.betweenClosed(pos.offset(-radius, -radius, -radius), pos.offset(radius, radius, radius))) {
            double distance = center.getCenter().distanceToSqr(origin);
            long order = center.asLong();
            if (distance > radius * radius || distance > bestDistance
                || (best != null && distance == bestDistance && order >= bestPosition)) continue;
            var candidate = sacrificeCandidate(level, center);
            if (candidate != null) {
                best = candidate;
                bestPosition = order;
                bestDistance = distance;
            }
        }
        if (best == null) return;
        victim.getPersistentData().putBoolean(used, true);
        if (best instanceof GoldenSacrificialBowlBlockEntity chalice) {
            chalice.notifySacrifice(victim);
            var ritual = (NativeCovenantRitual) chalice.getCurrentRitualRecipe().value().getRitual();
            message(player, "sacrificed", (ritual.durationTicks(level) + 19) / 20);
        } else if (best instanceof Covenant legacy) {
            legacy.sacrifice = victim.getUUID();
            legacy.state = State.RUNNING;
            legacy.setChanged();
            message(player, "sacrificed", (legacy.remaining + 19) / 20);
        }
    }

    private static net.minecraft.world.level.block.entity.BlockEntity sacrificeCandidate(ServerLevel level, BlockPos center) {
        if (!level.hasChunkAt(center) || !chalice(level.getBlockState(center))) return null;
        if (level.getBlockEntity(center) instanceof GoldenSacrificialBowlBlockEntity chalice) {
            var entry = chalice.getCurrentRitualRecipe();
            if (entry != null && entry.value().getRitual() instanceof NativeCovenantRitual ritual
                && entry.value().requiresSacrifice() && !chalice.sacrificeFulfilled() && NativeCovenantRitual.loaded(level, center)
                && ritual.isValid(level, center, chalice, null, chalice.itemStackHandler.getStackInSlot(0),
                    chalice.remainingAdditionalIngredients == null
                        ? com.klikli_dev.occultism.common.ritual.Ritual.getRemainingAdditionalIngredients(
                            entry.value().getIngredients(), chalice.consumedIngredients) : chalice.remainingAdditionalIngredients))
                return chalice;
        }
        BlockPos focus = center.offset(FOCUS);
        if (level.hasChunkAt(focus) && level.getBlockEntity(focus) instanceof Covenant legacy
            && legacy.state == State.WAITING_SACRIFICE && legacy.structure(level) == Structure.VALID) return legacy;
        return null;
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("state", state.name());
        tag.putInt("remaining", remaining);
        if (recipeId != null) tag.putString("recipe", recipeId.toString());
        if (chaliceId != null) tag.putString("chalice", chaliceId.toString());
        if (ritualId != null) tag.putUUID("ritual", ritualId);
        if (owner != null) tag.putUUID("owner", owner);
        if (sacrifice != null) tag.putUUID("sacrifice", sacrifice);
        if (!result.isEmpty()) tag.put("result", result.save(registries));
        ListTag items = new ListTag();
        for (ItemStack stack : escrow) if (!stack.isEmpty()) items.add(stack.save(registries));
        tag.put("escrow", items);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        try {
            state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException ignored) {
            state = State.IDLE;
        }
        remaining = Math.max(0, tag.getInt("remaining"));
        recipeId = ResourceLocation.tryParse(tag.getString("recipe"));
        chaliceId = ResourceLocation.tryParse(tag.getString("chalice"));
        ritualId = tag.hasUUID("ritual") ? tag.getUUID("ritual") : null;
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        sacrifice = tag.hasUUID("sacrifice") ? tag.getUUID("sacrifice") : null;
        result = ItemStack.parseOptional(registries, tag.getCompound("result"));
        escrow.clear();
        for (Tag encoded : tag.getList("escrow", Tag.TAG_COMPOUND))
            ItemStack.parse(registries, encoded).ifPresent(escrow::add);
    }
}
