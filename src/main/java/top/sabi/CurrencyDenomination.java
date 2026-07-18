package top.sabi;

import net.minecraft.world.item.Item;

public enum CurrencyDenomination {
    SMALL(1),
    MEDIUM(64),
    BIG(4096),
    GIANT(262144);

    private final long value;

    CurrencyDenomination(long value) {
        this.value = value;
    }

    public long value() {
        return this.value;
    }

    public Item item() {
        return switch (this) {
            case SMALL -> Sabi.SMALL_SABI;
            case MEDIUM -> Sabi.MEDIUM_SABI;
            case BIG -> Sabi.BIG_SABI;
            case GIANT -> Sabi.GIANT_SABI;
        };
    }
}
