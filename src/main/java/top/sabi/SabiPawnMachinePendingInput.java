package top.sabi;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class SabiPawnMachinePendingInput {
    public static final AttachmentType<List<ItemStack>> PENDING_INPUT = AttachmentRegistry.create(
            Sabi.id("pawn_machine_pending_input"),
            builder -> builder.initializer(ArrayList::new).persistent(ItemStack.CODEC.listOf()));

    private SabiPawnMachinePendingInput() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> restorePendingInputs(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Player player = handler.getPlayer();
            if (player.containerMenu instanceof SabiPawnMachineMenu menu) menu.returnPawnInputsToPlayer(player);
            restorePendingInputs(player);
            server.getPlayerList().saveAll();
        });
    }

    public static void setQuick(Player player, ItemStack stack) { set(player, 0, stack); }
    public static void setDetail(Player player, ItemStack stack) { set(player, 1, stack); }

    private static void set(Player player, int index, ItemStack stack) {
        List<ItemStack> values = new ArrayList<>(player.getAttachedOrCreate(PENDING_INPUT));
        while (values.size() <= index) values.add(ItemStack.EMPTY);
        values.set(index, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        player.setAttached(PENDING_INPUT, values);
    }

    private static void restorePendingInputs(Player player) {
        List<ItemStack> stacks = player.removeAttached(PENDING_INPUT);
        if (stacks == null) return;
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && !player.getInventory().add(stack)) player.drop(stack, false);
        }
        player.getInventory().setChanged();
    }
}
