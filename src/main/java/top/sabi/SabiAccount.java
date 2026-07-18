package top.sabi;

import java.util.Locale;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class SabiAccount {
    public static final AttachmentType<Long> ACCOUNT = AttachmentRegistry.create(
            Sabi.id("account"), builder -> builder.initializer(() -> 0L).persistent(Codec.LONG).copyOnDeath());

    private SabiAccount() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.getPlayer()));
    }

    public static long balance(Player player) {
        return player.getAttachedOrCreate(ACCOUNT);
    }

    public static void add(Player player, long amount) {
        if (amount > 0) player.setAttached(ACCOUNT, saturatingAdd(balance(player), amount));
    }

    public static boolean withdraw(Player player, long amount) {
        long balance = balance(player);
        if (amount <= 0 || balance < amount) return false;
        giveCurrency(player, amount);
        player.setAttached(ACCOUNT, balance - amount);
        return true;
    }

    public static boolean spend(Player player, long amount) {
        long balance = balance(player);
        if (amount < 0 || balance < amount) return false;
        player.setAttached(ACCOUNT, balance - amount);
        return true;
    }

    public static long depositAllCurrency(Player player) {
        long total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            CurrencyDenomination denomination = denominationOf(stack);
            if (denomination != null) {
                total = saturatingAdd(total, denomination.value() * stack.getCount());
                player.getInventory().removeItem(slot, stack.getCount());
            }
        }
        add(player, total);
        return total;
    }

    public static void sync(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            ServerPlayNetworking.send(serverPlayer, new SabiNetwork.BalanceSyncPayload(balance(player)));
        }
    }

    public static String format(long amount) { return String.format(Locale.ROOT, "%,d", amount); }

    private static CurrencyDenomination denominationOf(ItemStack stack) {
        if (stack.is(Sabi.SMALL_SABI)) return CurrencyDenomination.SMALL;
        if (stack.is(Sabi.MEDIUM_SABI)) return CurrencyDenomination.MEDIUM;
        if (stack.is(Sabi.BIG_SABI)) return CurrencyDenomination.BIG;
        if (stack.is(Sabi.GIANT_SABI)) return CurrencyDenomination.GIANT;
        return null;
    }

    private static void giveCurrency(Player player, long amount) {
        long remaining = amount;
        for (int i = CurrencyDenomination.values().length - 1; i >= 0; i--) {
            CurrencyDenomination denomination = CurrencyDenomination.values()[i];
            long count = remaining / denomination.value();
            remaining %= denomination.value();
            while (count > 0) {
                int stackSize = (int)Math.min(Sabi.CURRENCY_STACK_SIZE, count);
                ItemStack stack = new ItemStack(denomination.item(), stackSize);
                if (!player.getInventory().add(stack)) player.drop(stack, false);
                count -= stackSize;
            }
        }
    }

    private static long saturatingAdd(long left, long right) {
        long result = left + right;
        return result < 0 ? Long.MAX_VALUE : result;
    }
}
