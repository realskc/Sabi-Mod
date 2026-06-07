package top.sabi;

import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class SabiAccount {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Sabi.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<AccountData>> ACCOUNT = ATTACHMENTS.register(
            "account",
            () -> AttachmentType.serializable((Supplier<AccountData>)AccountData::new).copyOnDeath().sync(AccountData.STREAM_CODEC).build()
    );

    private SabiAccount() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(SabiAccount::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(SabiAccount::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(SabiAccount::onPlayerRespawn);
    }

    public static long balance(Player player) {
        return player.getData(ACCOUNT.get()).balance();
    }

    public static void add(Player player, long amount) {
        if (amount <= 0) {
            return;
        }
        AccountData data = player.getData(ACCOUNT.get());
        data.setBalance(saturatingAdd(data.balance(), amount));
    }

    public static boolean withdraw(Player player, long amount) {
        if (amount <= 0) {
            return false;
        }
        AccountData data = player.getData(ACCOUNT.get());
        if (data.balance() < amount) {
            return false;
        }
        giveCurrency(player, amount);
        data.setBalance(data.balance() - amount);
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
            PacketDistributor.sendToPlayer(serverPlayer, new SabiNetwork.BalanceSyncPayload(balance(player)));
        }
    }

    public static String format(long amount) {
        return String.format(Locale.ROOT, "%,d", amount);
    }

    private static CurrencyDenomination denominationOf(ItemStack stack) {
        if (stack.is(Sabi.SMALL_SABI.get())) {
            return CurrencyDenomination.SMALL;
        }
        if (stack.is(Sabi.MEDIUM_SABI.get())) {
            return CurrencyDenomination.MEDIUM;
        }
        if (stack.is(Sabi.BIG_SABI.get())) {
            return CurrencyDenomination.BIG;
        }
        if (stack.is(Sabi.GIANT_SABI.get())) {
            return CurrencyDenomination.GIANT;
        }
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
                ItemStack stack = new ItemStack(denomination.item().get(), stackSize);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                count -= stackSize;
            }
        }
    }

    private static long saturatingAdd(long left, long right) {
        long result = left + right;
        return result < 0 ? Long.MAX_VALUE : result;
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        sync(event.getEntity());
    }

    private static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        sync(event.getEntity());
    }

    private static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        sync(event.getEntity());
    }

    public static final class AccountData implements ValueIOSerializable {
        static final StreamCodec<RegistryFriendlyByteBuf, AccountData> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_LONG, AccountData::balance, AccountData::new);

        private long balance;

        public AccountData() {
            this(0);
        }

        public AccountData(long balance) {
            this.balance = Math.max(0, balance);
        }

        public long balance() {
            return this.balance;
        }

        public void setBalance(long balance) {
            this.balance = Math.max(0, balance);
        }

        @Override
        public void serialize(ValueOutput output) {
            output.putLong("balance", this.balance);
        }

        @Override
        public void deserialize(ValueInput input) {
            this.balance = Math.max(0, input.getLongOr("balance", 0));
        }
    }
}
