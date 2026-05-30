package com.chaseschwartz.extractcraft;

import org.slf4j.Logger;

import com.chaseschwartz.extractcraft.raid.ExtractionZoneHandler;
import com.chaseschwartz.extractcraft.raid.RaidCommands;
import com.chaseschwartz.extractcraft.raid.markers.RaidMarkerCommands;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(ExtractCraft.MODID)
public class ExtractCraft {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "extractcraft";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "extractcraft" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "extractcraft" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "extractcraft" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creates a new Block with the id "extractcraft:example_block", combining the namespace and path
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
    // Creates a new BlockItem with the id "extractcraft:example_block", combining the namespace and path
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);
    public static final DeferredBlock<Block> PLAYER_SPAWN_MARKER = registerMarkerBlock("player_spawn_marker", MapColor.COLOR_LIGHT_BLUE);
    public static final DeferredItem<BlockItem> PLAYER_SPAWN_MARKER_ITEM = ITEMS.registerSimpleBlockItem("player_spawn_marker", PLAYER_SPAWN_MARKER);
    public static final DeferredBlock<Block> EXTRACTION_MARKER = registerMarkerBlock("extraction_marker", MapColor.COLOR_GREEN);
    public static final DeferredItem<BlockItem> EXTRACTION_MARKER_ITEM = ITEMS.registerSimpleBlockItem("extraction_marker", EXTRACTION_MARKER);
    public static final DeferredBlock<Block> LOOT_MARKER = registerMarkerBlock("loot_marker", MapColor.GOLD);
    public static final DeferredItem<BlockItem> LOOT_MARKER_ITEM = ITEMS.registerSimpleBlockItem("loot_marker", LOOT_MARKER);
    public static final DeferredBlock<Block> RARE_LOOT_MARKER = registerMarkerBlock("rare_loot_marker", MapColor.COLOR_PURPLE);
    public static final DeferredItem<BlockItem> RARE_LOOT_MARKER_ITEM = ITEMS.registerSimpleBlockItem("rare_loot_marker", RARE_LOOT_MARKER);
    public static final DeferredBlock<Block> MOB_SPAWN_MARKER = registerMarkerBlock("mob_spawn_marker", MapColor.COLOR_RED);
    public static final DeferredItem<BlockItem> MOB_SPAWN_MARKER_ITEM = ITEMS.registerSimpleBlockItem("mob_spawn_marker", MOB_SPAWN_MARKER);

    // Creates a new food item with the id "extractcraft:example_id", nutrition 1 and saturation 2
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder()
            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));

    // Creates a creative tab with the id "extractcraft:example_tab" for the example item, that is placed after the combat tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.extractcraft")) //The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> EXAMPLE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(EXAMPLE_ITEM.get()); // Add the example item to the tab. For your own tabs, this method is preferred over the event
                output.accept(PLAYER_SPAWN_MARKER_ITEM.get());
                output.accept(EXTRACTION_MARKER_ITEM.get());
                output.accept(LOOT_MARKER_ITEM.get());
                output.accept(RARE_LOOT_MARKER_ITEM.get());
                output.accept(MOB_SPAWN_MARKER_ITEM.get());
            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public ExtractCraft(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (ExtractCraft) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new ExtractionZoneHandler());

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    private static DeferredBlock<Block> registerMarkerBlock(String id, MapColor mapColor) {
        return BLOCKS.registerSimpleBlock(id, BlockBehaviour.Properties.of()
                .mapColor(mapColor)
                .strength(0.0F)
                .noCollission()
                .lightLevel(state -> 12));
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(EXAMPLE_BLOCK_ITEM);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        RaidCommands.register(event.getDispatcher());
        RaidMarkerCommands.register(event.getDispatcher());
    }
}
