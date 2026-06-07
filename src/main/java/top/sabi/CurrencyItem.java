package top.sabi;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class CurrencyItem extends Item {
    private final CurrencyDenomination denomination;

    public CurrencyItem(Properties properties, CurrencyDenomination denomination) {
        super(properties);
        this.denomination = denomination;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return Sabi.CURRENCY_STACK_SIZE;
    }

    public CurrencyDenomination denomination() {
        return this.denomination;
    }
}
