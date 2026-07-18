package top.sabi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

public class SabiPawnMachineMenu extends AbstractContainerMenu {
    public static final int QUICK_PAWN_INPUT_SLOT = 0;
    public static final int DETAIL_PAWN_INPUT_SLOT = 1;
    public static final int PAWN_CONFIRM_BUTTON = 0;
    public static final int QUICK_PAWN_CONFIRM_BUTTON = 1;
    private static final int PLAYER_INVENTORY_START = 2;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 36;

    private final PawnInputContainer quickPawnInput;
    private final PawnInputContainer detailPawnInput;
    private final ContainerLevelAccess access;
    private final BlockPos pos;
    private final Player owner;
    private Item selectedItem;
    private boolean quickPawnInputActive;
    private boolean detailPawnInputActive;
    private boolean pendingInputSaveNeeded;

    public SabiPawnMachineMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(Sabi.PAWN_MACHINE_MENU, containerId);
        this.pos = pos;
        this.owner = playerInventory.player;
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), pos);
        this.quickPawnInput = new PawnInputContainer(playerInventory.player, true);
        this.detailPawnInput = new PawnInputContainer(playerInventory.player, false);

        this.addSlot(new PawnInputSlot(this.quickPawnInput, 0, 154, 28, false));
        this.addSlot(new PawnInputSlot(this.detailPawnInput, 0, 139, 65, true));
        this.addStandardInventorySlots(playerInventory, 29, 154);
    }

    public BlockPos pos() {
        return this.pos;
    }

    public void setPawnInputMode(boolean quickPawnInputActive, boolean detailPawnInputActive) {
        this.quickPawnInputActive = quickPawnInputActive;
        this.detailPawnInputActive = detailPawnInputActive;
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
            if (targetSlot < 0 || this.slots.get(targetSlot).hasItem() || !this.moveItemStackTo(source, targetSlot, targetSlot + 1, false)) {
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
        this.returnPawnInputsToPlayer(player);
        this.savePendingInputsIfNeeded();
    }

    public void returnPawnInputsToPlayer(Player player) {
        this.returnInputToPlayer(player, this.quickPawnInput);
        this.returnInputToPlayer(player, this.detailPawnInput);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, Sabi.PAWN_MACHINE);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        this.savePendingInputsIfNeeded();
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
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        ItemStack stack = input.getItem(0);
        if (stack.isEmpty()) {
            return;
        }

        SabiPawnMachineConfig.Config config = SabiPawnMachineConfig.load(serverPlayer.level().getServer());
        SabiNetwork.PawnContainerKind containerKind = pawnContainerKind(stack);
        if (!requireSelectedItem && containerKind != null && hasStoredItems(stack, containerKind)) {
            this.processContainerContents(serverPlayer, input, stack, containerKind, config);
            this.broadcastChanges();
            return;
        }

        ItemStack pawned = input.removeItemNoUpdate(0);
        if ((requireSelectedItem && (this.selectedItem == null || !pawned.is(this.selectedItem))) || !config.isAllowed(pawned.getItem())) {
            player.getInventory().placeItemBackInInventory(pawned);
            serverPlayer.sendSystemMessage(Component.translatable("message.sabi.sabi_machine.item_not_allowed"), true);
        } else {
            SabiPawnMachineStorage.get(serverPlayer.level().getServer()).store(pawned);
            SabiAccount.add(player, (long)config.price(pawned.getItem()).pawn() * pawned.getCount());
            SabiAccount.sync(player);
            SabiNetwork.refreshOpenPawnMachines(serverPlayer.level().getServer());
        }
        this.broadcastChanges();
    }

    private void processContainerContents(ServerPlayer player, SimpleContainer input, ItemStack container, SabiNetwork.PawnContainerKind containerKind, SabiPawnMachineConfig.Config config) {
        SabiPawnMachineStorage storage = SabiPawnMachineStorage.get(player.level().getServer());
        long payout = 0L;
        boolean storedAny = false;
        boolean rejectedAny = false;
        java.util.List<ItemStack> remainingItems = new java.util.ArrayList<>();

        for (ItemStack containedStack : containerItems(container, containerKind)) {
            if (config.isAllowed(containedStack.getItem())) {
                storage.store(containedStack);
                payout += (long)config.price(containedStack.getItem()).pawn() * containedStack.getCount();
                storedAny = true;
            } else {
                remainingItems.add(containedStack);
                rejectedAny = true;
            }
        }

        if (storedAny) {
            if (remainingItems.isEmpty()) {
                clearContainerItems(container, containerKind);
            } else {
                setContainerItems(container, containerKind, remainingItems);
            }
            input.setChanged();
            SabiAccount.add(player, payout);
            SabiAccount.sync(player);
            SabiNetwork.refreshOpenPawnMachines(player.level().getServer());
        }
        if (rejectedAny) {
            SabiNetwork.showContainerRejectedNotice(player, this.pos, containerKind);
        }
    }

    private static SabiNetwork.PawnContainerKind pawnContainerKind(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) {
            return SabiNetwork.PawnContainerKind.SHULKER_BOX;
        }
        if (stack.getItem() instanceof BundleItem) {
            return SabiNetwork.PawnContainerKind.BUNDLE;
        }
        return null;
    }

    private static boolean hasStoredItems(ItemStack stack, SabiNetwork.PawnContainerKind containerKind) {
        return switch (containerKind) {
            case SHULKER_BOX -> stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().findAny().isPresent();
            case BUNDLE -> !stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).isEmpty();
        };
    }

    private static java.util.List<ItemStack> containerItems(ItemStack stack, SabiNetwork.PawnContainerKind containerKind) {
        return switch (containerKind) {
            case SHULKER_BOX -> stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().toList();
            case BUNDLE -> stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).itemCopyStream().toList();
        };
    }

    private static void clearContainerItems(ItemStack stack, SabiNetwork.PawnContainerKind containerKind) {
        switch (containerKind) {
            case SHULKER_BOX -> stack.remove(DataComponents.CONTAINER);
            case BUNDLE -> stack.set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        }
    }

    private static void setContainerItems(ItemStack stack, SabiNetwork.PawnContainerKind containerKind, java.util.List<ItemStack> items) {
        switch (containerKind) {
            case SHULKER_BOX -> stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
            case BUNDLE -> {
                BundleContents.Mutable contents = new BundleContents.Mutable(BundleContents.EMPTY);
                for (ItemStack item : items) {
                    contents.tryInsert(item.copy());
                }
                stack.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
            }
        }
    }

    private void savePendingInputsIfNeeded() {
        if (!this.pendingInputSaveNeeded || !(this.owner instanceof ServerPlayer serverPlayer)) {
            return;
        }
        this.pendingInputSaveNeeded = false;
        serverPlayer.getInventory().setChanged();
        serverPlayer.level().getServer().getPlayerList().saveAll();
    }

    private class PawnInputContainer extends SimpleContainer {
        private final Player player;
        private final boolean quick;

        PawnInputContainer(Player player, boolean quick) {
            super(1);
            this.player = player;
            this.quick = quick;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack stack = super.removeItem(slot, amount);
            this.persist();
            return stack;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack stack = super.removeItemNoUpdate(slot);
            this.persist();
            return stack;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            super.setItem(slot, stack);
            this.persist();
        }

        @Override
        public void setChanged() {
            super.setChanged();
            this.persist();
        }

        @Override
        public void clearContent() {
            super.clearContent();
            this.persist();
        }

        private void persist() {
            if (this.player.level().isClientSide()) {
                return;
            }
            if (this.quick) {
                SabiPawnMachinePendingInput.setQuick(this.player, this.getItem(0));
            } else {
                SabiPawnMachinePendingInput.setDetail(this.player, this.getItem(0));
            }
            SabiPawnMachineMenu.this.pendingInputSaveNeeded = true;
        }
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
