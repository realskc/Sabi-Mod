package top.sabi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class SabiPawnMachineMenu extends AbstractContainerMenu {
    public static final int QUICK_PAWN_INPUT_SLOT = 0;
    public static final int DETAIL_PAWN_INPUT_SLOT = 1;
    public static final int PAWN_CONFIRM_BUTTON = 0;
    public static final int QUICK_PAWN_CONFIRM_BUTTON = 1;
    private static final int PLAYER_INVENTORY_START = 2;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 36;

    private final SimpleContainer quickPawnInput = new SimpleContainer(1);
    private final SimpleContainer detailPawnInput = new SimpleContainer(1);
    private final ContainerLevelAccess access;
    private final BlockPos pos;
    private Item selectedItem;
    private boolean quickPawnInputActive;
    private boolean detailPawnInputActive;
    private boolean processingPawnInput;

    public SabiPawnMachineMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readBlockPos());
    }

    public SabiPawnMachineMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(Sabi.PAWN_MACHINE_MENU.get(), containerId);
        this.pos = pos;
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), pos);

        this.addSlot(new PawnInputSlot(this.quickPawnInput, 0, 154, 28, false));
        this.addSlot(new PawnInputSlot(this.detailPawnInput, 0, 139, 65, true));
        this.addStandardInventorySlots(playerInventory, 29, 154);
    }

    public BlockPos pos() {
        return this.pos;
    }

    public void setPawnInputActive(boolean pawnInputActive) {
        this.setPawnInputMode(false, pawnInputActive);
    }

    public void setPawnInputMode(boolean quickPawnInputActive, boolean detailPawnInputActive) {
        this.quickPawnInputActive = quickPawnInputActive;
        this.detailPawnInputActive = detailPawnInputActive;
    }

    public boolean isPawnInputActive() {
        return this.quickPawnInputActive || this.detailPawnInputActive;
    }

    public void selectItem(Item item) {
        this.selectedItem = item;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        if (slotIndex == QUICK_PAWN_INPUT_SLOT || slotIndex == DETAIL_PAWN_INPUT_SLOT) {
            if (!this.moveItemStackTo(source, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            int targetSlot = activePawnInputSlot();
            if (targetSlot < 0 || !this.moveItemStackTo(source, targetSlot, targetSlot + 1, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (source.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return original;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == PAWN_CONFIRM_BUTTON) {
            this.processPawnInput(player, this.detailPawnInput, true);
            return true;
        }
        if (buttonId == QUICK_PAWN_CONFIRM_BUTTON) {
            this.processPawnInput(player, this.quickPawnInput, false);
            return true;
        }
        return false;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.returnInputToPlayer(player, this.quickPawnInput);
        this.returnInputToPlayer(player, this.detailPawnInput);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, Sabi.PAWN_MACHINE.get());
    }

    private int activePawnInputSlot() {
        if (this.quickPawnInputActive) {
            return QUICK_PAWN_INPUT_SLOT;
        }
        return this.detailPawnInputActive ? DETAIL_PAWN_INPUT_SLOT : -1;
    }

    private void returnInputToPlayer(Player player, SimpleContainer container) {
        if (!container.isEmpty()) {
            ItemStack stack = container.removeItemNoUpdate(0);
            if (!stack.isEmpty()) {
                player.getInventory().placeItemBackInInventory(stack);
            }
        }
    }

    private void processPawnInput(Player player, SimpleContainer input, boolean requireSelectedItem) {
        if (this.processingPawnInput || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        ItemStack stack = input.getItem(0);
        if (stack.isEmpty()) {
            return;
        }

        this.processingPawnInput = true;
        ItemStack pawned = input.removeItemNoUpdate(0);
        SabiPawnMachineConfig.Config config = SabiPawnMachineConfig.load(serverPlayer.level().getServer());
        if ((requireSelectedItem && (this.selectedItem == null || !pawned.is(this.selectedItem))) || !config.isAllowed(pawned.getItem())) {
            player.getInventory().placeItemBackInInventory(pawned);
            serverPlayer.sendSystemMessage(Component.translatable("message.sabi.sabi_machine.item_not_allowed"), true);
        } else {
            SabiPawnMachineStorage.get(serverPlayer.level().getServer()).store(pawned);
            SabiAccount.add(player, (long)config.price(pawned.getItem()).pawn() * pawned.getCount());
            SabiAccount.sync(player);
            SabiNetwork.refreshOpenPawnMachines(serverPlayer.level().getServer());
        }
        this.processingPawnInput = false;
        this.broadcastChanges();
    }

    private class PawnInputSlot extends Slot {
        private final boolean requiresSelectedItem;

        PawnInputSlot(Container container, int slot, int x, int y, boolean requiresSelectedItem) {
            super(container, slot, x, y);
            this.requiresSelectedItem = requiresSelectedItem;
        }

        @Override
        public boolean mayPlace(ItemStack itemStack) {
            if (itemStack.isEmpty()) {
                return false;
            }
            Identifier id = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
            return id != null && (!this.requiresSelectedItem || (SabiPawnMachineMenu.this.selectedItem != null && itemStack.is(SabiPawnMachineMenu.this.selectedItem)));
        }

        @Override
        public boolean isActive() {
            return this.requiresSelectedItem ? SabiPawnMachineMenu.this.detailPawnInputActive : SabiPawnMachineMenu.this.quickPawnInputActive;
        }
    }
}
