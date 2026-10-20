package com.castlevania;

import com.castlevania.entity.BibleEntity;
import com.castlevania.entity.CrossEntity;
import com.castlevania.entity.HolyWaterEntity;
import com.castlevania.item.BibleItem;
import com.castlevania.item.CrossItem;
import com.castlevania.item.HolyWaterItem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(CastlevaniaMod.MODID)
public final class CastlevaniaMod {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "castlevania";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "castlevania" namespace
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "castlevania" namespace
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "castlevania" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    // Create a Deferred Register to hold EntityTypes (the Cross projectile) registered under the "castlevania" namespace
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);

    // Creates a new Block with the id "castlevania:example_block", combining the namespace and path
    public static final RegistryObject<Block> EXAMPLE_BLOCK = BLOCKS.register("example_block",
        () -> new Block(BlockBehaviour.Properties.of()
            .setId(BLOCKS.key("example_block"))
            .mapColor(MapColor.STONE)
        )
    );
    // Creates a new BlockItem with the id "castlevania:example_block", combining the namespace and path
    public static final RegistryObject<Item> EXAMPLE_BLOCK_ITEM = ITEMS.register("example_block",
        () -> new BlockItem(EXAMPLE_BLOCK.get(), new Item.Properties().setId(ITEMS.key("example_block")))
    );

    // Creates a new food item with the id "castlevania:example_id", nutrition 1 and saturation 2
    public static final RegistryObject<Item> EXAMPLE_ITEM = ITEMS.register("example_item",
        () -> new Item(new Item.Properties()
            .setId(ITEMS.key("example_item"))
            .food(new FoodProperties.Builder()
                .alwaysEdible()
                .nutrition(1)
                .saturationModifier(2f)
                .build()
            )
        )
    );

    // The Cross: a throwable weapon that flies 10 blocks, waits 2 seconds and returns to the inventory
    public static final RegistryObject<Item> CROSS_ITEM = ITEMS.register("cross",
        () -> new CrossItem(new Item.Properties().setId(ITEMS.key("cross")).stacksTo(1))
    );

    // The thrown Cross entity
    public static final RegistryObject<EntityType<CrossEntity>> CROSS_ENTITY = ENTITY_TYPES.register("cross",
        () -> EntityType.Builder.<CrossEntity>of(CrossEntity::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .clientTrackingRange(8)
            .updateInterval(2)
            .build(ENTITY_TYPES.key("cross"))
    );

    // Holy Water: a throwable potion that ignites blocks on impact
    public static final RegistryObject<Item> HOLY_WATER_ITEM = ITEMS.register("holy_water",
        () -> new HolyWaterItem(new Item.Properties().setId(ITEMS.key("holy_water")).stacksTo(16))
    );

    // The thrown Holy Water entity
    public static final RegistryObject<EntityType<HolyWaterEntity>> HOLY_WATER_ENTITY = ENTITY_TYPES.register("holy_water",
        () -> EntityType.Builder.<HolyWaterEntity>of(HolyWaterEntity::new, MobCategory.MISC)
            .sized(0.25F, 0.25F)
            .clientTrackingRange(8)
            .updateInterval(2)
            .build(ENTITY_TYPES.key("holy_water"))
    );

    // The Bible: a thrown weapon that spins around the player for 5 seconds then disappears
    public static final RegistryObject<Item> BIBLE_ITEM = ITEMS.register("bible",
        () -> new BibleItem(new Item.Properties().setId(ITEMS.key("bible")).stacksTo(1))
    );

    // The thrown Bible entity
    public static final RegistryObject<EntityType<BibleEntity>> BIBLE_ENTITY = ENTITY_TYPES.register("bible",
        () -> EntityType.Builder.<BibleEntity>of(BibleEntity::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .clientTrackingRange(8)
            .updateInterval(2)
            .build(ENTITY_TYPES.key("bible"))
    );

    // Creates a creative tab for the Castlevania mod
    public static final RegistryObject<CreativeModeTab> CASTLEVANIA_TAB = CREATIVE_MODE_TABS.register("castlevania_weapons", () -> CreativeModeTab.builder()
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> CROSS_ITEM.get().getDefaultInstance())
            .title(Component.translatable("itemGroup.castlevania_weapons"))
            .displayItems((_, output) -> {
                output.accept(CROSS_ITEM.get());
                output.accept(HOLY_WATER_ITEM.get());
                output.accept(BIBLE_ITEM.get());
            }).build());

    public CastlevaniaMod(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();

        // Register the commonSetup method for modloading
        FMLCommonSetupEvent.getBus(modBusGroup).addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modBusGroup);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modBusGroup);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modBusGroup);
        // Register the Deferred Register to the mod event bus so the cross entity gets registered
        ENTITY_TYPES.register(modBusGroup);

        // Register the item to a creative tab
        BuildCreativeModeTabContentsEvent.BUS.addListener(CastlevaniaMod::addCreative);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.logDirtBlock)
            LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));

        LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);

        Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
    }

    // Add the example block item to the building blocks tab
    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS)
            event.accept(EXAMPLE_BLOCK_ITEM);
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            // The thrown Cross is rendered as its own item
            event.registerEntityRenderer(CROSS_ENTITY.get(), ThrownItemRenderer::new);
            // The thrown Holy Water is rendered as its own item
            event.registerEntityRenderer(HOLY_WATER_ENTITY.get(), ThrownItemRenderer::new);
            // The thrown Bible is rendered as its own item
            event.registerEntityRenderer(BIBLE_ENTITY.get(), ThrownItemRenderer::new);
        }
    }
}
