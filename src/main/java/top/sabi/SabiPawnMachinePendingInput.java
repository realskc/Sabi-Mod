package top.sabi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class SabiPawnMachinePendingInput {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Sabi.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PendingInputData>> PENDING_INPUT = ATTACHMENTS.register(
            "pawn_machine_pending_input",
            () -> AttachmentType.serializable((Supplier<PendingInputData>)PendingInputData::new).build()
    );

    private SabiPawnMachinePendingInput() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(SabiPawnMachinePendingInput::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(SabiPawnMachinePendingInput::onPlayerLoggedOut);
    }

    public static void setQuick(Player player, ItemStack stack) {
        player.getData(PENDING_INPUT.get()).setQuick(stack);
    }

    public static void setDetail(Player player, ItemStack stack) {
        player.getData(PENDING_INPUT.get()).setDetail(stack);
    }

    public static void clearQuick(Player player) {
        player.getData(PENDING_INPUT.get()).setQuick(ItemStack.EMPTY);
    }

    public static void clearDetail(Player player) {
        player.getData(PENDING_INPUT.get()).setDetail(ItemStack.EMPTY);
    }

    private static void restorePendingInputs(Player player) {
        PendingInputData data = player.getData(PENDING_INPUT.get());
        List<ItemStack> stacks = data.removeAll();
        for (ItemStack stack : stacks) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        if (!stacks.isEmpty()) {
            player.getInventory().setChanged();
        }
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        restorePendingInputs(event.getEntity());
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player.containerMenu instanceof SabiPawnMachineMenu menu) {
            menu.returnPawnInputsToPlayer(player);
        }
        restorePendingInputs(player);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.level().getServer().getPlayerList().saveAll();
        }
    }

    public static final class PendingInputData implements ValueIOSerializable {
        private ItemStack quick = ItemStack.EMPTY;
        private ItemStack detail = ItemStack.EMPTY;

        public void setQuick(ItemStack stack) {
            this.quick = copyOrEmpty(stack);
        }

        public void setDetail(ItemStack stack) {
            this.detail = copyOrEmpty(stack);
        }

        public List<ItemStack> removeAll() {
            List<ItemStack> stacks = new ArrayList<>();
            if (!this.quick.isEmpty()) {
                stacks.add(this.quick);
                this.quick = ItemStack.EMPTY;
            }
            if (!this.detail.isEmpty()) {
                stacks.add(this.detail);
                this.detail = ItemStack.EMPTY;
            }
            return stacks;
        }

        @Override
        public void serialize(ValueOutput output) {
            if (!this.quick.isEmpty()) {
                output.store("quick", ItemStack.OPTIONAL_CODEC, this.quick);
            }
            if (!this.detail.isEmpty()) {
                output.store("detail", ItemStack.OPTIONAL_CODEC, this.detail);
            }
        }

        @Override
        public void deserialize(ValueInput input) {
            this.quick = input.read("quick", ItemStack.OPTIONAL_CODEC).map(PendingInputData::copyOrEmpty).orElse(ItemStack.EMPTY);
            this.detail = input.read("detail", ItemStack.OPTIONAL_CODEC).map(PendingInputData::copyOrEmpty).orElse(ItemStack.EMPTY);
        }

        private static ItemStack copyOrEmpty(ItemStack stack) {
            return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }
    }
}
