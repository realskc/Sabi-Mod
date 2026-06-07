package top.sabi;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Sabi.MOD_ID)
public class Sabi {
    public static final String MOD_ID = "sabi";
    public static final int CURRENCY_STACK_SIZE = 64;
    public static final int CURRENCY_EXCHANGE_RATE = 64;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

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

    public static final DeferredBlock<Block> PAWN_MACHINE = BLOCKS.registerSimpleBlock(
            "pawn_machine",
            () -> BlockBehaviour.Properties.of()
                    .strength(50.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
    );

    public static final DeferredItem<BlockItem> PAWN_MACHINE_ITEM = ITEMS.registerItem(
            "pawn_machine",
            properties -> new BlockItem(PAWN_MACHINE.get(), properties)
    );

    public Sabi(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
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
