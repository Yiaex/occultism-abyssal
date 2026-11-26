package dev.abyssalmining;

import com.klikli_dev.occultism.Occultism;
import com.klikli_dev.occultism.common.ritual.RitualFactory;
import com.klikli_dev.occultism.registry.OccultismRituals;
import com.klikli_dev.occultism.common.item.spirit.MinerSpiritItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.CreativeModeTabs;
import java.util.List;
import java.util.function.Supplier;

@Mod(Abyssal.ID)
public final class Abyssal {
    public static final String ID = "occultism_abyssal";
    public static final String LEGACY_ID = "occultism_abyssal_mining";
    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ID);

    static final DeferredRegister<RitualFactory> RITUALS = DeferredRegister.create(OccultismRituals.RITUAL_FACTORIES_KEY, ID);
    static {
        RITUALS.register("covenant_craft", () -> new RitualFactory(NativeCovenantRitual::new));
        for (String ritual : Settings.RITUAL_OUTPUTS)
            ITEMS.register("ritual_dummy/" + ritual, () -> new Item(new Item.Properties().stacksTo(1)));
    }

    static final Supplier<MachineBlock> MINE = BLOCKS.register("abyssal_mineshaft", () -> new MachineBlock(false));
    static final Supplier<MachineBlock> ARENA = BLOCKS.register("advanced_battlefield", () -> new MachineBlock(true));
    static final Supplier<Block> RUNE = BLOCKS.register("abyssal_rune", () -> new RuneBlock(BlockBehaviour.Properties.of().strength(0.15f).noCollission().noOcclusion().lightLevel(s -> 4)));
    static final Supplier<Block> ANCHOR = BLOCKS.register("abyssal_covenant_pentacle", () -> new RuneBlock.Anchor(BlockBehaviour.Properties.of().strength(0.15f).noCollission().noOcclusion().lightLevel(s -> 6)));
    static final Supplier<BlockItem> MINE_ITEM = machineItem("abyssal_mineshaft", MINE);
    static final Supplier<BlockItem> ARENA_ITEM = machineItem("advanced_battlefield", ARENA);
    static final Supplier<Item> STACK = card("stack_upgrade");
    static final Supplier<Item> PARALLEL = card("parallel_upgrade");
    static final Supplier<Item> LOOT = card("looting_upgrade");
    static final Supplier<Item> ME = card("me_export_upgrade");
    static final Supplier<Item> MAINTENANCE = card("maintenance_upgrade");
    static final Supplier<Item> MINER = ITEMS.register("miner_ancient_abyssal", () -> new MinerSpiritItem(
        new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()
            .component(DataComponents.UNBREAKABLE, new Unbreakable(true)),
        Occultism.STARTUP_CONFIG.dimensionalMineshaft.minerAncientEldritch.maxMiningTime,
        Occultism.STARTUP_CONFIG.dimensionalMineshaft.minerAncientEldritch.rollsPerOperation,
        () -> 0) {
        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags) {
            super.appendHoverText(stack, context, lines, flags);
            addTooltip(lines, getDescriptionId() + ".tooltip.detail");
        }
    });

    static final Supplier<BlockEntityType<Machine>> MACHINE = ENTITIES.register("machine",
        () -> BlockEntityType.Builder.of(Machine::new, MINE.get(), ARENA.get()).build(null));
    static final Supplier<BlockEntityType<RuneBlock.Mark>> RUNE_MARK = ENTITIES.register("abyssal_rune",
        () -> BlockEntityType.Builder.of(RuneBlock.Mark::new, RUNE.get()).build(null));
    static final Supplier<BlockEntityType<Covenant>> COVENANT = ENTITIES.register("abyssal_covenant_pentacle",
        () -> BlockEntityType.Builder.of(Covenant::new, ANCHOR.get()).build(null));
    static final Supplier<MenuType<MachineMenu>> MENU = MENUS.register("machine",
        () -> IMenuTypeExtension.create(MachineMenu::new));

    public Abyssal(IEventBus bus, ModContainer container) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) ClientScreens.registerCompatibility();
        registerWithLegacyAliases(BLOCKS, bus);
        registerWithLegacyAliases(ITEMS, bus);
        registerWithLegacyAliases(ENTITIES, bus);
        registerWithLegacyAliases(MENUS, bus);
        registerWithLegacyAliases(RITUALS, bus);
        bus.addListener(this::capabilities);
        bus.addListener(this::creative);
        container.registerConfig(ModConfig.Type.SERVER, Settings.SPEC);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, Covenant::interact);
        NeoForge.EVENT_BUS.addListener(Mining::reload);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, Covenant::sacrifice);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, Covenant::breakPart);
    }

    private static void registerWithLegacyAliases(DeferredRegister<?> registry, IEventBus bus) {
        for (var entry : registry.getEntries()) {
            var id = entry.getId();
            registry.addAlias(ResourceLocation.fromNamespaceAndPath(LEGACY_ID, id.getPath()), id);
        }
        registry.register(bus);
    }

    private static Supplier<Item> card(String path) {
        return ITEMS.register(path, () -> new Item(new Item.Properties()) {
            @Override
            public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags) {
                addTooltip(lines, getDescriptionId() + ".tooltip");
            }
        });
    }

    private static Supplier<BlockItem> machineItem(String path, Supplier<? extends Block> block) {
        return ITEMS.register(path, () -> new BlockItem(block.get(), new Item.Properties()) {
            @Override
            public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags) {
                addTooltip(lines, getDescriptionId() + ".tooltip");
                addTooltip(lines, getDescriptionId() + ".tooltip.detail");
            }
        });
    }

    private static void addTooltip(List<Component> lines, String key) {
        lines.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }

    private void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MACHINE.get(), Machine::automation);
        if (net.neoforged.fml.ModList.get().isLoaded("ae2")) AeExport.register(event);
    }

    private void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) {
            for (var item : java.util.List.of(MINE_ITEM, ARENA_ITEM, MINER, STACK, PARALLEL, LOOT, MAINTENANCE))
                event.accept(item.get());
            if (net.neoforged.fml.ModList.get().isLoaded("ae2")) event.accept(ME.get());
        }
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }
}
