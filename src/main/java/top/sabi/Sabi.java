package top.sabi;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class Sabi implements ModInitializer {
    public static final String MOD_ID = "sabi";
    public static final int CURRENCY_STACK_SIZE = 64;

    public static final Item SMALL_SABI = new CurrencyItem(itemProperties("small_sabi"), CurrencyDenomination.SMALL);
    public static final Item MEDIUM_SABI = new CurrencyItem(itemProperties("medium_sabi"), CurrencyDenomination.MEDIUM);
    public static final Item BIG_SABI = new CurrencyItem(itemProperties("big_sabi"), CurrencyDenomination.BIG);
    public static final Item GIANT_SABI = new CurrencyItem(itemProperties("giant_sabi"), CurrencyDenomination.GIANT);
    public static final Item ADVANCED_REDSTONE_CORE = new Item(itemProperties("advanced_redstone_core"));
    public static final SabiPawnMachineBlock PAWN_MACHINE = new SabiPawnMachineBlock(
            BlockBehaviour.Properties.of().setId(blockKey("sabi_machine"))
                    .strength(50.0F, 1200.0F).requiresCorrectToolForDrops().sound(SoundType.STONE));
    public static final Item PAWN_MACHINE_ITEM = new BlockItem(
            PAWN_MACHINE, itemProperties("sabi_machine").useBlockDescriptionPrefix());
    public static final MenuType<SabiPawnMachineMenu> PAWN_MACHINE_MENU =
            new ExtendedMenuType<>(SabiPawnMachineMenu::new, BlockPos.STREAM_CODEC);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private static ResourceKey<Item> itemKey(String path) {
        return ResourceKey.create(Registries.ITEM, id(path));
    }

    private static ResourceKey<Block> blockKey(String path) {
        return ResourceKey.create(Registries.BLOCK, id(path));
    }

    private static Item.Properties itemProperties(String path) {
        return new Item.Properties().setId(itemKey(path));
    }

    @Override
    public void onInitialize() {
        register(BuiltInRegistries.ITEM, "small_sabi", SMALL_SABI);
        register(BuiltInRegistries.ITEM, "medium_sabi", MEDIUM_SABI);
        register(BuiltInRegistries.ITEM, "big_sabi", BIG_SABI);
        register(BuiltInRegistries.ITEM, "giant_sabi", GIANT_SABI);
        register(BuiltInRegistries.ITEM, "advanced_redstone_core", ADVANCED_REDSTONE_CORE);
        register(BuiltInRegistries.BLOCK, "sabi_machine", PAWN_MACHINE);
        register(BuiltInRegistries.ITEM, "sabi_machine", PAWN_MACHINE_ITEM);
        register(BuiltInRegistries.MENU, "sabi_machine", PAWN_MACHINE_MENU);

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(entries -> {
            entries.accept(SMALL_SABI);
            entries.accept(MEDIUM_SABI);
            entries.accept(BIG_SABI);
            entries.accept(GIANT_SABI);
            entries.accept(ADVANCED_REDSTONE_CORE);
            entries.accept(PAWN_MACHINE_ITEM);
        });
        SabiAccount.register();
        SabiPawnMachinePendingInput.register();
        SabiNetwork.registerServer();
    }

    private static <T> T register(net.minecraft.core.Registry<T> registry, String path, T value) {
        return net.minecraft.core.Registry.register(registry, id(path), value);
    }
}
