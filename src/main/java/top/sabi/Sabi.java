package top.sabi;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.network.IContainerFactory;

@Mod(Sabi.MOD_ID)
public class Sabi {
    public static final String MOD_ID = "sabi";
    public static final int CURRENCY_STACK_SIZE = 64;
    public static final int CURRENCY_EXCHANGE_RATE = 64;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(BuiltInRegistries.MENU, MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, MOD_ID);

    public static final DeferredItem<Item> SMALL_SABI = ITEMS.registerItem(
            "small_sabi",
            properties -> new CurrencyItem(properties, CurrencyDenomination.SMALL)
    );

    public static final DeferredItem<Item> MEDIUM_SABI = ITEMS.registerItem(
            "medium_sabi",
            properties -> new CurrencyItem(properties, CurrencyDenomination.MEDIUM)
    );

    public static final DeferredItem<Item> BIG_SABI = ITEMS.registerItem(
            "big_sabi",
            properties -> new CurrencyItem(properties, CurrencyDenomination.BIG)
    );

    public static final DeferredItem<Item> GIANT_SABI = ITEMS.registerItem(
            "giant_sabi",
            properties -> new CurrencyItem(properties, CurrencyDenomination.GIANT)
    );

    public static final DeferredItem<Item> ADVANCED_REDSTONE_CORE = ITEMS.registerSimpleItem(
            "advanced_redstone_core",
            Item.Properties::new
    );

    public static final DeferredBlock<SabiPawnMachineBlock> PAWN_MACHINE = BLOCKS.registerBlock(
            "sabi_machine",
            SabiPawnMachineBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .strength(50.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
    );

    public static final DeferredHolder<MenuType<?>, MenuType<SabiPawnMachineMenu>> PAWN_MACHINE_MENU = MENUS.register(
            "sabi_machine",
            () -> new MenuType<>((IContainerFactory<SabiPawnMachineMenu>)SabiPawnMachineMenu::new, FeatureFlags.VANILLA_SET)
    );

    public static final DeferredItem<BlockItem> PAWN_MACHINE_ITEM = ITEMS.registerItem(
            "sabi_machine",
            properties -> new BlockItem(PAWN_MACHINE.get(), properties)
    );

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CurrencyStackUpgradeRecipe>> SMALL_TO_MEDIUM_SABI_RECIPE = RECIPE_SERIALIZERS.register(
            "small_sabi_to_medium_sabi",
            () -> CurrencyStackUpgradeRecipe.serializer(SMALL_SABI, MEDIUM_SABI)
    );

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CurrencyStackUpgradeRecipe>> MEDIUM_TO_BIG_SABI_RECIPE = RECIPE_SERIALIZERS.register(
            "medium_sabi_to_big_sabi",
            () -> CurrencyStackUpgradeRecipe.serializer(MEDIUM_SABI, BIG_SABI)
    );

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CurrencyStackUpgradeRecipe>> BIG_TO_GIANT_SABI_RECIPE = RECIPE_SERIALIZERS.register(
            "big_sabi_to_giant_sabi",
            () -> CurrencyStackUpgradeRecipe.serializer(BIG_SABI, GIANT_SABI)
    );

    public Sabi(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        MENUS.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        SabiCurrencyExchange.register();
        SabiAccount.register(modEventBus);
        SabiNetwork.register(modEventBus);
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            top.sabi.client.SabiClient.register(modEventBus);
        }
        modEventBus.addListener(this::addCreative);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(SMALL_SABI);
            event.accept(MEDIUM_SABI);
            event.accept(BIG_SABI);
            event.accept(GIANT_SABI);
            event.accept(ADVANCED_REDSTONE_CORE);
            event.accept(PAWN_MACHINE_ITEM);
        }
    }
}
