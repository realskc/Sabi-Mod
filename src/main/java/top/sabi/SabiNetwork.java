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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class SabiNetwork {
    private SabiNetwork() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(SabiNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Sabi.MOD_ID).versioned("1");
        registrar.playToClient(BalanceSyncPayload.TYPE, BalanceSyncPayload.STREAM_CODEC, SabiNetwork::handleBalanceSync);
        registrar.playToClient(PawnMachinePayload.TYPE, PawnMachinePayload.STREAM_CODEC, SabiNetwork::handlePawnMachineOpen);
        registrar.playToClient(PawnMachineNoticePayload.TYPE, PawnMachineNoticePayload.STREAM_CODEC, SabiNetwork::handlePawnMachineNotice);
        registrar.playToServer(AccountActionPayload.TYPE, AccountActionPayload.STREAM_CODEC, SabiNetwork::handleAccountAction);
        registrar.playToServer(PawnMachineInputModePayload.TYPE, PawnMachineInputModePayload.STREAM_CODEC, SabiNetwork::handlePawnMachineInputMode);
        registrar.playToServer(PawnMachineSelectPayload.TYPE, PawnMachineSelectPayload.STREAM_CODEC, SabiNetwork::handlePawnMachineSelect);
        registrar.playToServer(PawnMachineRedeemPayload.TYPE, PawnMachineRedeemPayload.STREAM_CODEC, SabiNetwork::handlePawnMachineRedeem);
        registrar.playToServer(PawnMachineBuyPayload.TYPE, PawnMachineBuyPayload.STREAM_CODEC, SabiNetwork::handlePawnMachineBuy);
    }

    private static void handleBalanceSync(BalanceSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SabiClientState.setBalance(payload.balance()));
    }

    private static void handlePawnMachineOpen(PawnMachinePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> top.sabi.client.SabiClient.openPawnMachine(payload));
    }

    private static void handlePawnMachineNotice(PawnMachineNoticePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> top.sabi.client.SabiClient.showPawnMachineNotice(payload));
    }

    private static void handleAccountAction(AccountActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }

            switch (payload.action()) {
                case DEPOSIT_ALL -> SabiAccount.depositAllCurrency(player);
                case WITHDRAW -> SabiAccount.withdraw(player, payload.amount());
            }
            serverPlayer.inventoryMenu.sendAllDataToRemote();
            serverPlayer.containerMenu.sendAllDataToRemote();
            SabiAccount.sync(player);
        });
    }

    private static void handlePawnMachineInputMode(PawnMachineInputModePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player instanceof ServerPlayer serverPlayer && serverPlayer.containerMenu instanceof SabiPawnMachineMenu menu && menu.pos().equals(payload.pos())) {
                menu.setPawnInputMode(payload.quickPawnInputActive(), payload.detailPawnInputActive());
            }
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

            if (!isPawnMachine(serverPlayer, payload.pos()) || !isNear(serverPlayer, payload.pos())) {
                return;
            }

            SabiPawnMachineConfig.Config config = SabiPawnMachineConfig.load(serverPlayer.level().getServer());
            if (!config.isAllowed(item)) {
                refreshPawnMachine(serverPlayer, payload.pos());
                return;
            }

            SabiPawnMachineConfig.Price price = config.price(item);
            int amount = Math.max(1, payload.amount());
            if (redeem(serverPlayer, SabiPawnMachineStorage.get(serverPlayer.level().getServer()), item, price.redeem(), amount)) {
                SabiAccount.sync(serverPlayer);
                refreshOpenPawnMachines(serverPlayer.level().getServer());
            } else {
                SabiAccount.sync(serverPlayer);
                refreshPawnMachine(serverPlayer, payload.pos());
            }
        });
    }

    private static void handlePawnMachineBuy(PawnMachineBuyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }

            Item item = BuiltInRegistries.ITEM.getValue(payload.itemId());
            if (item == null) {
                return;
            }

            if (!isPawnMachine(serverPlayer, payload.pos()) || !isNear(serverPlayer, payload.pos())) {
                return;
            }

            SabiPawnMachineConfig.Config config = SabiPawnMachineConfig.load(serverPlayer.level().getServer());
            if (!config.isAllowed(item)) {
                refreshPawnMachine(serverPlayer, payload.pos());
                return;
            }

            SabiPawnMachineStorage storage = SabiPawnMachineStorage.get(serverPlayer.level().getServer());
            SabiPawnMachineConfig.Price price = config.price(item);
            int amount = Math.max(1, payload.amount());
            buy(serverPlayer, storage, item, price.pawn(), amount);
            SabiAccount.sync(serverPlayer);
            refreshPawnMachine(serverPlayer, payload.pos());
        });
    }

    public static void openPawnMachine(ServerPlayer player, BlockPos pos) {
        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, menuPlayer) -> new SabiPawnMachineMenu(containerId, inventory, pos),
                        Component.translatable("screen.sabi.sabi_machine")
                ),
                buffer -> buffer.writeBlockPos(pos)
        );
        refreshPawnMachine(player, pos);
    }

    public static void refreshPawnMachine(ServerPlayer player, BlockPos pos) {
        SabiPawnMachineConfig.Config config = SabiPawnMachineConfig.load(player.level().getServer());
        SabiPawnMachineStorage storage = SabiPawnMachineStorage.get(player.level().getServer());
        List<PawnMachineItemEntry> entries = new ArrayList<>();
        for (Item item : config.allowedItems()) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null) {
                SabiPawnMachineConfig.Price price = config.price(item);
                entries.add(new PawnMachineItemEntry(id, storage.storedCount(item), price.pawn(), price.redeem()));
            }
        }
        PacketDistributor.sendToPlayer(player, new PawnMachinePayload(pos, entries));
    }

    public static void refreshOpenPawnMachines(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof SabiPawnMachineMenu menu) {
                refreshPawnMachine(player, menu.pos());
            }
        }
    }

    public static void showShulkerRejectedNotice(ServerPlayer player, BlockPos pos) {
        PacketDistributor.sendToPlayer(player, new PawnMachineNoticePayload(pos, PawnMachineNotice.SHULKER_CONTAINS_UNPAWNABLE));
    }

    private static boolean redeem(ServerPlayer player, SabiPawnMachineStorage storage, Item item, int redeemPrice, int amount) {
        int maxStackSize = new ItemStack(item).getMaxStackSize();
        if (amount > maxStackSize) {
            player.sendSystemMessage(Component.translatable("message.sabi.sabi_machine.amount_too_large", maxStackSize), true);
            return false;
        }
        if (storage.storedCount(item) < amount) {
            player.sendSystemMessage(Component.translatable("message.sabi.sabi_machine.stock_insufficient"), true);
            return false;
        }

        long cost = (long)redeemPrice * amount;
        if (!SabiAccount.spend(player, cost)) {
            player.sendSystemMessage(Component.translatable("message.sabi.sabi_machine.account_insufficient"), true);
            return false;
        }

        giveStacks(player, storage.take(item, amount));
        return true;
    }

    private static boolean buy(ServerPlayer player, SabiPawnMachineStorage storage, Item item, int pawnPrice, int amount) {
        int maxStackSize = new ItemStack(item).getMaxStackSize();
        if (amount > maxStackSize) {
            player.sendSystemMessage(Component.translatable("message.sabi.sabi_machine.amount_too_large", maxStackSize), true);
            return false;
        }
        if (storage.storedCount(item) > 0) {
            player.sendSystemMessage(Component.translatable("message.sabi.sabi_machine.stock_available"), true);
            return false;
        }

        long cost = (long)Math.max(0, pawnPrice) * 4L * amount;
        if (!SabiAccount.spend(player, cost)) {
            player.sendSystemMessage(Component.translatable("message.sabi.sabi_machine.account_insufficient"), true);
            return false;
        }

        giveStacks(player, List.of(new ItemStack(item, amount)));
        return true;
    }

    private static void giveStacks(Player player, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    private static boolean isNear(Player player, BlockPos pos) {
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }

    private static boolean isPawnMachine(ServerPlayer player, BlockPos pos) {
        return player.level().getBlockState(pos).is(Sabi.PAWN_MACHINE.get());
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

    public record PawnMachineNoticePayload(BlockPos pos, PawnMachineNotice notice) implements CustomPacketPayload {
        public static final Type<PawnMachineNoticePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Sabi.MOD_ID, "sabi_machine_notice"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PawnMachineNoticePayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC,
                PawnMachineNoticePayload::pos,
                ByteBufCodecs.VAR_INT.map(PawnMachineNotice::byId, PawnMachineNotice::id),
                PawnMachineNoticePayload::notice,
                PawnMachineNoticePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public enum PawnMachineNotice {
        SHULKER_CONTAINS_UNPAWNABLE(0);

        private final int id;

        PawnMachineNotice(int id) {
            this.id = id;
        }

        public int id() {
            return this.id;
        }

        public static PawnMachineNotice byId(int id) {
            return SHULKER_CONTAINS_UNPAWNABLE;
        }
    }

    public record PawnMachineInputModePayload(BlockPos pos, boolean quickPawnInputActive, boolean detailPawnInputActive) implements CustomPacketPayload {
        public static final Type<PawnMachineInputModePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Sabi.MOD_ID, "sabi_machine_input_mode"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PawnMachineInputModePayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC,
                PawnMachineInputModePayload::pos,
                ByteBufCodecs.BOOL,
                PawnMachineInputModePayload::quickPawnInputActive,
                ByteBufCodecs.BOOL,
                PawnMachineInputModePayload::detailPawnInputActive,
                PawnMachineInputModePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
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

    public record PawnMachineBuyPayload(BlockPos pos, Identifier itemId, int amount) implements CustomPacketPayload {
        public static final Type<PawnMachineBuyPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Sabi.MOD_ID, "sabi_machine_buy"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PawnMachineBuyPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC,
                PawnMachineBuyPayload::pos,
                Identifier.STREAM_CODEC,
                PawnMachineBuyPayload::itemId,
                ByteBufCodecs.VAR_INT,
                PawnMachineBuyPayload::amount,
                PawnMachineBuyPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
