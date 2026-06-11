package com.chaseschwartz.extractcraft;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import com.chaseschwartz.extractcraft.itemvalues.ItemValueCommands;
import com.chaseschwartz.extractcraft.itemvalues.ItemValueRegistry;
import com.chaseschwartz.extractcraft.items.ExtractCraftItemMetadata;
import com.chaseschwartz.extractcraft.items.ExtractCraftProfiledItem;
import com.chaseschwartz.extractcraft.items.LooseLootDefinition;
import com.chaseschwartz.extractcraft.network.ExtractCraftNetwork;
import com.chaseschwartz.extractcraft.gameplay.ExtractCraftGameplayRulesHandler;
import com.chaseschwartz.extractcraft.raid.ExtractionZoneHandler;
import com.chaseschwartz.extractcraft.raid.RaidCommands;
import com.chaseschwartz.extractcraft.raid.containers.ActiveLootContainerInteractionHandler;
import com.chaseschwartz.extractcraft.raid.containers.ActiveLootContainerMenu;
import com.chaseschwartz.extractcraft.raid.inventory.BaseStashMenu;
import com.chaseschwartz.extractcraft.raid.containers.RaidMapCommands;
import com.chaseschwartz.extractcraft.raid.inventory.ItemCarryProfileRegistry;
import com.chaseschwartz.extractcraft.raid.inventory.ManagedDropService;
import com.chaseschwartz.extractcraft.raid.inventory.PostRaidResultMenu;
import com.chaseschwartz.extractcraft.raid.inventory.RaidEquipmentSlot;
import com.chaseschwartz.extractcraft.raid.inventory.RaidInventoryCommands;
import com.chaseschwartz.extractcraft.raid.inventory.RaidInventoryMenu;
import com.chaseschwartz.extractcraft.raid.map.RaidMapBakeService;
import com.chaseschwartz.extractcraft.raid.map.RaidMapBoundaryService;
import com.chaseschwartz.extractcraft.timedaction.TimedActionEventHandler;
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
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
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
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.IContainerFactory;
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
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final List<DeferredItem<Item>> PROFILED_ITEMS = new ArrayList<>();

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
    public static final DeferredHolder<MenuType<?>, MenuType<ActiveLootContainerMenu>> ACTIVE_LOOT_CONTAINER_MENU = MENUS.register("active_loot_container",
            () -> new MenuType<>((IContainerFactory<ActiveLootContainerMenu>) ActiveLootContainerMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<RaidInventoryMenu>> RAID_INVENTORY_MENU = MENUS.register("raid_inventory",
            () -> new MenuType<>((IContainerFactory<RaidInventoryMenu>) RaidInventoryMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<BaseStashMenu>> BASE_STASH_MENU = MENUS.register("base_stash",
            () -> new MenuType<>((IContainerFactory<BaseStashMenu>) BaseStashMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<PostRaidResultMenu>> POST_RAID_RESULT_MENU = MENUS.register("post_raid_result",
            () -> new MenuType<>((IContainerFactory<PostRaidResultMenu>) PostRaidResultMenu::new, FeatureFlags.VANILLA_SET));

    // Creates a new food item with the id "extractcraft:example_id", nutrition 1 and saturation 2
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder()
            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));
    public static final DeferredItem<Item> SCRAPLINE_HELMET = registerProfiledItem("scrapline_helmet", helmetMeta(1, "A scuffed raid helmet that keeps the basics covered.", 2.2D, 80, 1));
    public static final DeferredItem<Item> RANGER_BALLISTIC_HELMET = registerProfiledItem("ranger_ballistic_helmet", helmetMeta(2, "A field helmet with reinforced ballistic plating.", 2.8D, 120, 2));
    public static final DeferredItem<Item> VECTOR_RAIL_HELMET = registerProfiledItem("vector_rail_helmet", helmetMeta(3, "A rail-mounted combat helmet built for extended raids.", 3.3D, 170, 3));
    public static final DeferredItem<Item> PHANTOM_COMMS_HELMET = registerProfiledItem("phantom_comms_helmet", helmetMeta(4, "An advanced helmet with sealed comms hardware.", 3.8D, 230, 4), false);
    public static final DeferredItem<Item> APEX_ASSAULT_HELMET = registerProfiledItem("apex_assault_helmet", helmetMeta(4, "Elite assault headgear for the worst parts of the city.", 4.2D, 230, 5));

    public static final DeferredItem<Item> SOFTSHELL_PLATE_CARRIER = registerProfiledItem("softshell_plate_carrier", armorMeta(1, "A light plate carrier for low-risk scav runs.", 5.0D, 120, 1, 2, 3));
    public static final DeferredItem<Item> BULWARK_PLATE_CARRIER = registerProfiledItem("bulwark_plate_carrier", armorMeta(2, "A balanced carrier with thicker front and side plates.", 6.6D, 180, 2, 3, 3));
    public static final DeferredItem<Item> WARDEN_COMBAT_ARMOR = registerProfiledItem("warden_combat_armor", armorMeta(3, "Heavy combat armor built to take sustained punishment.", 8.2D, 260, 4, 3, 3));
    public static final DeferredItem<Item> JUGGERNAUT_ASSAULT_ARMOR = registerProfiledItem("juggernaut_assault_armor", armorMeta(4, "Elite assault armor with dense plating and hard points.", 10.0D, 360, 5, 3, 3));

    public static final DeferredItem<Item> SCOUT_CHEST_RIG = registerProfiledItem("scout_chest_rig", vestMeta(1, "A compact rig for light raids and fast exits.", 2.4D, 90, 3, 2, 8.0D, 2, 2));
    public static final DeferredItem<Item> RANGEFINDER_TACTICAL_VEST = registerProfiledItem("rangefinder_tactical_vest", vestMeta(2, "A tactical vest with enough space for a proper kit.", 3.1D, 130, 4, 2, 12.0D, 2, 2));
    public static final DeferredItem<Item> OPERATOR_LOAD_BEARING_VEST = registerProfiledItem("operator_load_bearing_vest", vestMeta(3, "A load-bearing vest with a broad combat layout.", 3.8D, 180, 4, 3, 16.0D, 2, 3));
    public static final DeferredItem<Item> SPECTER_COMBAT_RIG = registerProfiledItem("specter_combat_rig", vestMeta(4, "A high-capacity combat rig for long urban pushes.", 4.6D, 240, 5, 3, 20.0D, 2, 3));
    public static final DeferredItem<Item> ARSENAL_ELITE_VEST = registerProfiledItem("arsenal_elite_vest", vestMeta(5, "An elite rig with dense pouches and strong retention.", 5.3D, 310, 5, 4, 24.0D, 2, 3), false);

    public static final DeferredItem<Item> SPARROW_SLING_PACK = registerProfiledItem("sparrow_sling_pack", backpackMeta(1, "A nimble sling pack for small hauls.", 1.2D, 100, 4, 4, 14.0D, 2, 2));
    public static final DeferredItem<Item> FIELDRUNNER_PACK = registerProfiledItem("fieldrunner_pack", backpackMeta(2, "A reliable field pack with room for essentials.", 1.8D, 150, 5, 5, 22.0D, 2, 2));
    public static final DeferredItem<Item> MULE_TACTICAL_PACK = registerProfiledItem("mule_tactical_pack", backpackMeta(3, "A tactical pack with a practical raid footprint.", 2.6D, 220, 6, 6, 32.0D, 2, 3));
    public static final DeferredItem<Item> ATLAS_RAID_PACK = registerProfiledItem("atlas_raid_pack", backpackMeta(4, "A large raid pack for serious extraction runs.", 3.6D, 300, 7, 7, 44.0D, 3, 3));
    public static final DeferredItem<Item> ATLAS_RAID_PACK_MK2 = registerProfiledItem("atlas_raid_pack_mk2", backpackMeta(5, "A reinforced Atlas variant with maximum storage.", 4.4D, 390, 8, 8, 56.0D, 3, 3), false);

    public static final DeferredItem<Item> PIONEER_LOCKBOX = registerProfiledItem("pioneer_lockbox", safeMeta(1, "A small lockbox that secures the absolute essentials.", 1.5D, 90, 2, 2, 2, 2));
    public static final DeferredItem<Item> BLACKSITE_SECURE_CASE = registerProfiledItem("blacksite_secure_case", safeMeta(2, "A reinforced case with more room for high-value finds.", 2.2D, 160, 3, 2, 2, 2));
    public static final DeferredItem<Item> OMEGA_SAFE_CONTAINER = registerProfiledItem("omega_safe_container", safeMeta(3, "A premium safe container for critical extraction loot.", 3.2D, 260, 3, 3, 2, 3));

    public static final DeferredItem<Item> COMBAT_STIM_SYRINGE = registerProfiledItem("combat_stim_syringe", medMeta(1, "A fast injector for emergency field stabilization.", 0.3D, 1, 1, 8, 40));
    public static final DeferredItem<Item> FIELD_MED_KIT = registerProfiledItem("field_med_kit", medMeta(2, "A compact trauma pack for controlled recovery.", 0.8D, 1, 2, 20, 60));
    public static final DeferredItem<Item> TRAUMA_RESPONSE_CASE = registerProfiledItem("trauma_response_case", medMeta(3, "A sealed advanced medical case for severe injuries.", 1.4D, 2, 2, 45, 80));
    public static final DeferredItem<Item> QUICKCLOT_INJECTOR = registerProfiledItem("quickclot_injector", medMeta(1, "A fast injector for emergency field stabilization.", 0.3D, 1, 1, 20, 40), false);
    public static final DeferredItem<Item> TRAUMA_FIELD_PACK = registerProfiledItem("trauma_field_pack", medMeta(2, "A compact trauma pack for controlled recovery.", 0.8D, 1, 2, 45, 80), false);
    public static final DeferredItem<Item> BLACKSEAL_MED_CASE = registerProfiledItem("blackseal_med_case", medMeta(3, "A sealed advanced medical case for severe injuries.", 1.4D, 2, 2, 80, 120), false);

    public static final DeferredItem<Item> HELMET_REBUILD_KIT = registerProfiledItem("helmet_rebuild_kit", repairMeta("Helmet", "A compact kit for future helmet durability repairs.", 0.9D, 2, 2, 90));
    public static final DeferredItem<Item> ARMOR_REBUILD_KIT = registerProfiledItem("armor_rebuild_kit", repairMeta("Armor", "A heavy kit for future armor plate rebuilds.", 1.8D, 2, 3, 160));
    public static final DeferredItem<Item> PACK_REBUILD_KIT = registerProfiledItem("pack_rebuild_kit", repairMeta("Backpack", "A repair bundle for future pack and strap damage.", 1.2D, 2, 2, 120));
    public static final List<DeferredItem<Item>> LOOSE_LOOT_ITEMS = registerLooseLootItems();

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
                PROFILED_ITEMS.forEach(item -> output.accept(item.get()));
            }).build());

    private static DeferredItem<Item> registerProfiledItem(String id, ExtractCraftItemMetadata metadata) {
        return registerProfiledItem(id, metadata, true);
    }

    private static DeferredItem<Item> registerProfiledItem(String id, ExtractCraftItemMetadata metadata, boolean creativeTab) {
        DeferredItem<Item> item = ITEMS.register(id, () -> new ExtractCraftProfiledItem(profiledProperties(metadata), metadata));
        if (creativeTab) {
            PROFILED_ITEMS.add(item);
        }
        return item;
    }

    private static Item.Properties profiledProperties(ExtractCraftItemMetadata metadata) {
        Item.Properties properties = new Item.Properties();
        if (metadata.equipmentSlot().isPresent()) {
            return properties.stacksTo(1);
        }
        if (metadata.healAmount().isPresent()) {
            return properties.stacksTo(1);
        }
        if (metadata.consumable() || metadata.repairTargetCategory().isPresent()) {
            return properties.stacksTo(16);
        }
        return properties.stacksTo(32);
    }

    private static ExtractCraftItemMetadata helmetMeta(int tier, String description, double weight, int durability, int armorRating) {
        return ExtractCraftItemMetadata.builder(tier, "Helmet", description, weight, 2, 2)
                .equipmentSlot(RaidEquipmentSlot.HELMET)
                .durability("Helmet", durability)
                .armorRating(armorRating)
                .tooltip("Head protection metadata is ready; damage behavior is not wired yet.")
                .build();
    }

    private static ExtractCraftItemMetadata armorMeta(int tier, String description, double weight, int durability, int armorRating, int gridWidth, int gridHeight) {
        return ExtractCraftItemMetadata.builder(tier, "Armor", description, weight, gridWidth, gridHeight)
                .equipmentSlot(RaidEquipmentSlot.ARMOR)
                .durability("Armor", durability)
                .armorRating(armorRating)
                .tooltip("Body armor metadata is ready; plate damage behavior is not wired yet.")
                .build();
    }

    private static ExtractCraftItemMetadata vestMeta(int tier, String description, double weight, int durability, int storageWidth, int storageHeight, double carryWeight, int gridWidth, int gridHeight) {
        return ExtractCraftItemMetadata.builder(tier, "Vest", description, weight, gridWidth, gridHeight)
                .equipmentSlot(RaidEquipmentSlot.EQUIPPED_VEST)
                .storageGrid(storageWidth, storageHeight, carryWeight)
                .tooltip("Provides the equipped vest grid. Category restrictions are intentionally loose for now.")
                .build();
    }

    private static ExtractCraftItemMetadata backpackMeta(int tier, String description, double weight, int durability, int storageWidth, int storageHeight, double carryWeight, int gridWidth, int gridHeight) {
        return ExtractCraftItemMetadata.builder(tier, "Backpack", description, weight, gridWidth, gridHeight)
                .equipmentSlot(RaidEquipmentSlot.EQUIPPED_BACKPACK)
                .storageGrid(storageWidth, storageHeight, carryWeight)
                .durability("Backpack", durability)
                .tooltip("Provides the equipped backpack grid.")
                .build();
    }

    private static ExtractCraftItemMetadata safeMeta(int tier, String description, double weight, int durability, int storageWidth, int storageHeight, int gridWidth, int gridHeight) {
        return ExtractCraftItemMetadata.builder(tier, "Safe Container", description, weight, gridWidth, gridHeight)
                .equipmentSlot(RaidEquipmentSlot.EQUIPPED_SAFE_CONTAINER)
                .storageGrid(storageWidth, storageHeight, 6.0D + tier * 2.0D)
                .tooltip("Contents use the existing safe-box death/failure preservation rules.")
                .build();
    }

    private static ExtractCraftItemMetadata medMeta(int tier, String description, double weight, int gridWidth, int gridHeight, int healAmount, int useTicks) {
        return ExtractCraftItemMetadata.builder(tier, "Medical", description, weight, gridWidth, gridHeight)
                .healing(healAmount, useTicks)
                .allowInSafeBox(true)
                .tooltip("Healing behavior is configured for a later pass.")
                .build();
    }

    private static ExtractCraftItemMetadata repairMeta(String target, String description, double weight, int gridWidth, int gridHeight, int repairAmount) {
        return ExtractCraftItemMetadata.builder(1, "Repair Kit", description, weight, gridWidth, gridHeight)
                .repairKit(target, repairAmount)
                .tooltip("Repair behavior is configured for a later pass.")
                .build();
    }

    private static List<DeferredItem<Item>> registerLooseLootItems() {
        List<DeferredItem<Item>> items = new ArrayList<>();
        for (LooseLootDefinition definition : LooseLootDefinition.DEFINITIONS) {
            items.add(registerProfiledItem(definition.id(), looseLootMeta(definition)));
        }
        return List.copyOf(items);
    }

    private static ExtractCraftItemMetadata looseLootMeta(LooseLootDefinition definition) {
        return ExtractCraftItemMetadata.builder(
                definition.tier(),
                looseLootCategoryLabel(definition.category()),
                definition.description(),
                definition.weight(),
                definition.gridWidth(),
                definition.gridHeight())
                .tooltip("Loose loot barter item.")
                .build();
    }

    private static String looseLootCategoryLabel(String category) {
        String[] words = category.replace('-', '_').split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public ExtractCraft(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ExtractCraftNetwork::register);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);
        MENUS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (ExtractCraft) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new ExtractCraftGameplayRulesHandler());
        NeoForge.EVENT_BUS.register(new ExtractionZoneHandler());
        NeoForge.EVENT_BUS.register(new RaidMapBakeService());
        NeoForge.EVENT_BUS.register(new RaidMapBoundaryService());
        NeoForge.EVENT_BUS.register(new ActiveLootContainerInteractionHandler());
        NeoForge.EVENT_BUS.register(new ManagedDropService());
        NeoForge.EVENT_BUS.register(new TimedActionEventHandler());

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
        RaidMapCommands.register(event.getDispatcher());
        ItemValueCommands.register(event.getDispatcher());
        RaidInventoryCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ItemValueRegistry());
        event.addListener(new ItemCarryProfileRegistry());
    }
}
