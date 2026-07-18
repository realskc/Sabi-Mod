package top.sabi;

import net.minecraft.world.item.Item;

public class CurrencyItem extends Item {
    private final CurrencyDenomination denomination;

    public CurrencyItem(Properties properties, CurrencyDenomination denomination) {
        super(properties);
        this.denomination = denomination;
    }

    public CurrencyDenomination denomination() {
        return this.denomination;
    }
}
