package top.sabi;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
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
        registrar.playToServer(AccountActionPayload.TYPE, AccountActionPayload.STREAM_CODEC, SabiNetwork::handleAccountAction);
    }

    private static void handleBalanceSync(BalanceSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SabiClientState.setBalance(payload.balance()));
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
}
