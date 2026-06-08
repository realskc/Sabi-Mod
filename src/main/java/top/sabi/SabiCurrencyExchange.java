package top.sabi;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class SabiCurrencyExchange {
    private SabiCurrencyExchange() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SabiCurrencyExchange::onItemCrafted);
    }

    private static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Item lowerDenomination = lowerDenominationFor(event.getCrafting().getItem());
        if (lowerDenomination == null) {
            return;
        }

        Container craftingGrid = event.getInventory();
        for (int slot = 0; slot < craftingGrid.getContainerSize(); slot++) {
            ItemStack stack = craftingGrid.getItem(slot);
            if (stack.is(lowerDenomination) && stack.getCount() >= Sabi.CURRENCY_EXCHANGE_RATE) {
                stack.shrink(Sabi.CURRENCY_EXCHANGE_RATE - 1);
                craftingGrid.setChanged();
                return;
            }
        }
    }

    private static Item lowerDenominationFor(Item item) {
        if (item == Sabi.MEDIUM_SABI.get()) {
            return Sabi.SMALL_SABI.get();
        }
        if (item == Sabi.BIG_SABI.get()) {
            return Sabi.MEDIUM_SABI.get();
        }
        if (item == Sabi.GIANT_SABI.get()) {
            return Sabi.BIG_SABI.get();
        }
        return null;
    }
}
