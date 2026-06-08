package top.sabi;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class SabiNetwork {
    private static final int MAX_PAWN_MACHINE_REDEEM_AMOUNT = 99999;

    private SabiNetwork() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(SabiNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Sabi.MOD_ID).versioned("1");
        registrar.playToClient(BalanceSyncPayload.TYPE, BalanceSyncPayload.STREAM_CODEC, SabiNetwork::handleBalanceSync);
        registrar.playToClient(PawnMachinePayload.TYPE, PawnMachinePayload.STREAM_CODEC, SabiNetwork::handlePawnMachineOpen);
        registrar.playToServer(AccountActionPayload.TYPE, AccountActionPayload.STREAM_CODEC, SabiNetwork::handleAccountAction);
        registrar.playToServer(PawnMachineSelectPayload.TYPE, PawnMachineSelectPayload.STREAM_CODEC, SabiNetwork::handlePawnMachineSelect);
        registrar.playToServer(PawnMachineRedeemPayload.TYPE, PawnMachineRedeemPayload.STREAM_CODEC, SabiNetwork::handlePawnMachineRedeem);
    }

    private static void handleBalanceSync(BalanceSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SabiClientState.setBalance(payload.balance()));
    }

    private static void handlePawnMachineOpen(PawnMachinePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> top.sabi.client.SabiClient.openPawnMachine(payload));
    }

    private static void handleAccountAction(AccountActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player instanceof ServerPlayer)) {
                return;
            }

            switch (payload.action()) {
                case DEPOSIT_ALL -> SabiAccount.depositAllCurrency(player);
                case WITHDRAW -> SabiAccount.withdraw(player, payload.amount());
            }
            SabiAccount.sync(player);
        });
    }

    private static void handlePawnMachineSelect(PawnMachineSelectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer) || !(serverPlayer.containerMenu instanceof SabiPawnMachineMenu menu) || !menu.pos().equals(payload.pos())) {
                return;
            }

            Item item = BuiltInRegistries.ITEM.getValue(payload.itemId());
            if (item != null) {
                menu.selectItem(item);
            }
        });
    }

    private static void handlePawnMachineRedeem(PawnMachineRedeemPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }

            Item item = BuiltInRegistries.ITEM.getValue(payload.itemId());
            if (item == null) {
                return;
            }

            BlockEntity blockEntity = serverPlayer.level().getBlockEntity(payload.pos());
            if (!(blockEntity instanceof SabiPawnMachineBlockEntity machine) || !isNear(serverPlayer, payload.pos())) {
                return;
            }

            SabiPawnMachineConfig.Config config = SabiPawnMachineConfig.load(serverPlayer.level().getServer());
            if (!config.isAllowed(item)) {
                refreshPawnMachine(serverPlayer, payload.pos(), machine);
                return;
            }

            SabiPawnMachineConfig.Price price = config.price(item);
            int amount = Math.max(1, Math.min(MAX_PAWN_MACHINE_REDEEM_AMOUNT, payload.amount()));
            redeem(serverPlayer, machine, item, price.redeem(), amount);

            refreshPawnMachine(serverPlayer, payload.pos(), machine);
        });
    }

    public static void openPawnMachine(ServerPlayer player, BlockPos pos, SabiPawnMachineBlockEntity machine) {
        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, menuPlayer) -> new SabiPawnMachineMenu(containerId, inventory, machine, pos),
                        Component.translatable("screen.sabi.sabi_machine")
                ),
                buffer -> buffer.writeBlockPos(pos)
        );
        refreshPawnMachine(player, pos, machine);
    }

    public static void refreshPawnMachine(ServerPlayer player, BlockPos pos, SabiPawnMachineBlockEntity machine) {
        SabiPawnMachineConfig.Config config = SabiPawnMachineConfig.load(player.level().getServer());
        List<PawnMachineItemEntry> entries = new ArrayList<>();
        for (Item item : config.allowedItems()) {
            Identifier id = SabiPawnMachineBlockEntity.itemId(item);
            if (id != null) {
                SabiPawnMachineConfig.Price price = config.price(item);
                entries.add(new PawnMachineItemEntry(id, machine.storedCount(item), price.pawn(), price.redeem()));
            }
        }
        PacketDistributor.sendToPlayer(player, new PawnMachinePayload(pos, entries));
    }

    private static boolean redeem(ServerPlayer player, SabiPawnMachineBlockEntity machine, Item item, int redeemPrice, int amount) {
        int maxStackSize = new ItemStack(item).getMaxStackSize();
        if (amount > maxStackSize) {
            player.sendSystemMessage(Component.translatable("message.sabi.sabi_machine.amount_too_large", maxStackSize), true);
            return false;
        }
        if (machine.storedCount(item) < amount) {
            player.sendSystemMessage(Component.translatable("message.sabi.sabi_machine.stock_insufficient"), true);
            return false;
        }

        long cost = (long)redeemPrice * amount;
        if (inventoryCount(player, Sabi.SMALL_SABI.get()) < cost) {
            player.sendSystemMessage(Component.translatable("message.sabi.sabi_machine.currency_insufficient"), true);
            return false;
        }

        takeSmallCurrency(player, cost);
        giveStacks(player, machine.take(item, amount));
        return true;
    }

    static int inventoryCount(Player player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    static void giveStacks(Player player, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    static void giveSmallCurrency(Player player, long amount) {
        long remaining = Math.max(0L, amount);
        while (remaining > 0) {
            int stackSize = (int)Math.min(Sabi.CURRENCY_STACK_SIZE, remaining);
            ItemStack stack = new ItemStack(Sabi.SMALL_SABI.get(), stackSize);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            remaining -= stackSize;
        }
    }

    private static boolean takeSmallCurrency(Player player, long amount) {
        long required = Math.max(0L, amount);
        if (required == 0) {
            return true;
        }

        long available = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Sabi.SMALL_SABI.get())) {
                available += stack.getCount();
                if (available >= required) {
                    break;
                }
            }
        }
        if (available < required) {
            return false;
        }

        long remaining = required;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Sabi.SMALL_SABI.get())) {
                int toRemove = (int)Math.min(remaining, stack.getCount());
                player.getInventory().removeItem(slot, toRemove);
                remaining -= toRemove;
            }
        }
        return true;
    }

    private static boolean isNear(Player player, BlockPos pos) {
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }

    public record BalanceSyncPayload(long balance) implements CustomPacketPayload {
        public static final Type<BalanceSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Sabi.MOD_ID, "balance_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BalanceSyncPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_LONG, BalanceSyncPayload::balance, BalanceSyncPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record AccountActionPayload(AccountAction action, long amount) implements CustomPacketPayload {
        public static final Type<AccountActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Sabi.MOD_ID, "account_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AccountActionPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.map(AccountAction::byId, AccountAction::id),
                AccountActionPayload::action,
                ByteBufCodecs.VAR_LONG,
                AccountActionPayload::amount,
                AccountActionPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public enum AccountAction {
        DEPOSIT_ALL(0),
        WITHDRAW(1);

        private final int id;

        AccountAction(int id) {
            this.id = id;
        }

        public int id() {
            return this.id;
        }

        public static AccountAction byId(int id) {
            return id == WITHDRAW.id ? WITHDRAW : DEPOSIT_ALL;
        }
    }

    public record PawnMachinePayload(BlockPos pos, List<PawnMachineItemEntry> items) implements CustomPacketPayload {
        public static final Type<PawnMachinePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Sabi.MOD_ID, "sabi_machine"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PawnMachinePayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC,
                PawnMachinePayload::pos,
                PawnMachineItemEntry.STREAM_CODEC.apply(ByteBufCodecs.list(65536)),
                PawnMachinePayload::items,
                PawnMachinePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PawnMachineItemEntry(Identifier itemId, int storedCount, int pawnPrice, int redeemPrice) {
        public static final StreamCodec<RegistryFriendlyByteBuf, PawnMachineItemEntry> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC,
                PawnMachineItemEntry::itemId,
                ByteBufCodecs.VAR_INT,
                PawnMachineItemEntry::storedCount,
                ByteBufCodecs.VAR_INT,
                PawnMachineItemEntry::pawnPrice,
                ByteBufCodecs.VAR_INT,
                PawnMachineItemEntry::redeemPrice,
                PawnMachineItemEntry::new
        );
    }

    public record PawnMachineSelectPayload(BlockPos pos, Identifier itemId) implements CustomPacketPayload {
        public static final Type<PawnMachineSelectPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Sabi.MOD_ID, "sabi_machine_select"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PawnMachineSelectPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC,
                PawnMachineSelectPayload::pos,
                Identifier.STREAM_CODEC,
                PawnMachineSelectPayload::itemId,
                PawnMachineSelectPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PawnMachineRedeemPayload(BlockPos pos, Identifier itemId, int amount) implements CustomPacketPayload {
        public static final Type<PawnMachineRedeemPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Sabi.MOD_ID, "sabi_machine_redeem"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PawnMachineRedeemPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC,
                PawnMachineRedeemPayload::pos,
                Identifier.STREAM_CODEC,
                PawnMachineRedeemPayload::itemId,
                ByteBufCodecs.VAR_INT,
                PawnMachineRedeemPayload::amount,
                PawnMachineRedeemPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
