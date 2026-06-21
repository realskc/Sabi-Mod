package top.sabi.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import top.sabi.SabiNetwork;
import top.sabi.SabiPawnMachineMenu;
import top.sabi.SabiAccount;
import top.sabi.SabiClientState;

public class SabiPawnMachineScreen extends AbstractContainerScreen<SabiPawnMachineMenu> {
    private static final int IMAGE_WIDTH = 244;
    private static final int IMAGE_HEIGHT = 238;
    private static final int GRID_COLUMNS = 9;
    private static final int GRID_VISIBLE_ROWS = 4;
    private static final int CELL_SIZE = 18;
    private static final int GRID_WIDTH = GRID_COLUMNS * CELL_SIZE;
    private static final int GRID_HEIGHT = GRID_VISIBLE_ROWS * CELL_SIZE;

    private List<Row> rows = List.of();
    private List<Row> filteredRows = List.of();
    private Identifier selectedItemId;
    private EditBox searchBox;
    private EditBox redeemAmountBox;
    private Button quickPawnButton;
    private Button pawnConfirmButton;
    private Button redeemButton;
    private Button backButton;
    private Button quickPawnConfirmButton;
    private Button pawnCancelButton;
    private Button emptyShulkerConfirmButton;
    private Button emptyShulkerCancelButton;
    private Button doneButton;
    private PageMode pageMode = PageMode.GRID;
    private int scroll;

    public SabiPawnMachineScreen(SabiPawnMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
    }

    public boolean sameMachine(BlockPos pos) {
        return this.menu.pos().equals(pos);
    }

    public void update(SabiNetwork.PawnMachinePayload payload) {
        List<Row> nextRows = new ArrayList<>();
        int originalOrder = 0;
        for (SabiNetwork.PawnMachineItemEntry entry : payload.items()) {
            Item item = BuiltInRegistries.ITEM.getValue(entry.itemId());
            if (item != null) {
                nextRows.add(new Row(entry.itemId(), item, entry.storedCount(), entry.pawnPrice(), entry.redeemPrice(), originalOrder));
            }
            originalOrder++;
        }
        nextRows.sort((left, right) -> {
            int storedCountCompare = Integer.compare(right.storedCount, left.storedCount);
            if (storedCountCompare != 0) {
                return storedCountCompare;
            }
            return Integer.compare(left.originalOrder, right.originalOrder);
        });
        this.rows = nextRows;
        this.rebuildFilteredRows();

        if (this.selectedItemId != null && this.selectedRow() == null) {
            this.selectedItemId = null;
            this.pageMode = PageMode.GRID;
        }
    }

    @Override
    protected void init() {
        super.init();

        this.searchBox = new EditBox(this.font, this.leftPos + 30, this.topPos + 28, 118, 20, Component.translatable("screen.sabi.sabi_machine.search"));
        this.searchBox.setMaxLength(64);
        this.searchBox.setResponder(value -> {
            this.scroll = 0;
            this.rebuildFilteredRows();
        });
        this.addRenderableWidget(this.searchBox);

        this.quickPawnButton = this.addRenderableWidget(Button.builder(Component.translatable("button.sabi.pawn"), button -> this.handleQuickPawnButton())
                .bounds(this.leftPos + 176, this.topPos + 28, 42, 20)
                .build());

        this.redeemAmountBox = new EditBox(this.font, this.leftPos + 139, this.topPos + 86, 34, 20, Component.translatable("screen.sabi.sabi_machine.amount"));
        this.redeemAmountBox.setMaxLength(3);
        this.redeemAmountBox.setValue("");
        this.redeemAmountBox.setResponder(value -> {
            this.normalizeAmountBox();
            this.updateWidgetStates();
        });
        this.addRenderableWidget(this.redeemAmountBox);

        this.pawnConfirmButton = this.addRenderableWidget(Button.builder(Component.translatable("button.sabi.confirm"), button -> sendPawnConfirm())
                .bounds(this.leftPos + 179, this.topPos + 63, 42, 20)
                .build());
        this.redeemButton = this.addRenderableWidget(Button.builder(Component.translatable("button.sabi.confirm"), button -> sendSecondLineAction())
                .bounds(this.leftPos + 179, this.topPos + 86, 42, 20)
                .build());
        this.backButton = this.addRenderableWidget(Button.builder(Component.translatable("button.sabi.back"), button -> {
                    this.pageMode = PageMode.GRID;
                    this.menu.setPawnInputMode(false, false);
                    this.updateWidgetStates();
                })
                .bounds(this.leftPos + 90, this.topPos + 126, 64, 20)
                .build());

        this.quickPawnConfirmButton = this.addRenderableWidget(Button.builder(Component.translatable("button.sabi.confirm"), button -> {
                    this.sendQuickPawnConfirm();
                    this.pageMode = PageMode.GRID;
                    this.updateWidgetStates();
                })
                .bounds(this.leftPos + 54, this.topPos + 112, 64, 20)
                .build());
        this.pawnCancelButton = this.addRenderableWidget(Button.builder(Component.translatable("button.sabi.cancel"), button -> {
                    this.pageMode = PageMode.GRID;
                    this.updateWidgetStates();
                })
                .bounds(this.leftPos + 128, this.topPos + 112, 64, 20)
                .build());

        this.emptyShulkerConfirmButton = this.addRenderableWidget(Button.builder(Component.translatable("button.sabi.confirm"), button -> {
                    this.sendQuickPawnConfirm();
                    this.pageMode = PageMode.GRID;
                    this.updateWidgetStates();
                })
                .bounds(this.leftPos + 54, this.topPos + 97, 64, 20)
                .build());
        this.emptyShulkerCancelButton = this.addRenderableWidget(Button.builder(Component.translatable("button.sabi.cancel"), button -> {
                    this.pageMode = PageMode.GRID;
                    this.updateWidgetStates();
                })
                .bounds(this.leftPos + 128, this.topPos + 97, 64, 20)
                .build());

        this.doneButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(this.leftPos + IMAGE_WIDTH - 60, this.topPos + 128, 48, 20)
                .build());
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int panelX = this.leftPos;
        int panelY = this.topPos;

        this.updateWidgetStates();
        graphics.fill(panelX, panelY, panelX + IMAGE_WIDTH, panelY + IMAGE_HEIGHT, 0xEE101014);
        graphics.outline(panelX, panelY, IMAGE_WIDTH, IMAGE_HEIGHT, 0xFF39C37D);
        graphics.centeredText(this.font, this.title, this.width / 2, panelY + 10, 0xFFE7FFE9);

        if (this.pageMode == PageMode.DETAIL) {
            this.renderDetailPage(graphics, panelX, panelY);
        } else if (this.pageMode == PageMode.PAWN_CONFIRM) {
            this.renderPawnConfirmPage(graphics, panelX, panelY);
        } else if (this.pageMode == PageMode.EMPTY_SHULKER_CONFIRM) {
            this.renderEmptyShulkerConfirmPage(graphics, panelX, panelY);
        } else {
            this.renderSearchIcon(graphics, panelX + 14, panelY + 33);
            this.renderGridPage(graphics, mouseX, mouseY, panelX, panelY);
        }

        this.renderSlotBackgrounds(graphics);
        super.extractContents(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && this.pageMode == PageMode.GRID) {
            int index = this.gridIndexAt(event.x(), event.y());
            if (index >= 0 && index < this.filteredRows.size()) {
                this.selectedItemId = this.filteredRows.get(index).itemId;
                this.pageMode = PageMode.DETAIL;
                this.menu.selectItem(this.filteredRows.get(index).item);
                this.menu.setPawnInputMode(false, true);
                ClientPacketDistributor.sendToServer(new SabiNetwork.PawnMachineSelectPayload(this.menu.pos(), this.selectedItemId));
                this.updateWidgetStates();
                return true;
            }
        }

        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (this.pageMode == PageMode.GRID && this.isInGridBounds(x, y)) {
            this.scroll -= scrollY > 0 ? 1 : -1;
            this.clampScroll();
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.minecraft.options.keyInventory.isActiveAndMatches(InputConstants.getKey(event))) {
            return !this.isEditBoxFocused();
        }
        return super.keyPressed(event);
    }

    private void renderGridPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int panelX, int panelY) {
        int gridX = this.gridX();
        int gridY = this.gridY();

        graphics.fill(gridX - 4, gridY - 4, gridX + GRID_WIDTH + 4, gridY + GRID_HEIGHT + 4, 0xAA000000);
        graphics.outline(gridX - 4, gridY - 4, GRID_WIDTH + 8, GRID_HEIGHT + 8, 0xFF555555);

        this.clampScroll();
        int firstIndex = this.scroll * GRID_COLUMNS;
        for (int slot = 0; slot < GRID_COLUMNS * GRID_VISIBLE_ROWS; slot++) {
            int sourceIndex = firstIndex + slot;
            int column = slot % GRID_COLUMNS;
            int row = slot / GRID_COLUMNS;
            int cellX = gridX + column * CELL_SIZE;
            int cellY = gridY + row * CELL_SIZE;

            if (sourceIndex >= this.filteredRows.size()) {
                this.drawEmptyCell(graphics, cellX, cellY);
                continue;
            }

            Row itemRow = this.filteredRows.get(sourceIndex);
            this.drawItemCell(graphics, itemRow, cellX, cellY, mouseX, mouseY);
        }

        graphics.text(this.font, Component.translatable("screen.sabi.sabi_machine.result_count", this.filteredRows.size(), this.rows.size()), panelX + 12, panelY + 134, 0xFFB8FFD1, false);
    }

    private void renderPawnConfirmPage(GuiGraphicsExtractor graphics, int panelX, int panelY) {
        int contentX = panelX + 22;
        int contentY = panelY + 42;
        int contentWidth = IMAGE_WIDTH - 44;
        int contentHeight = 82;

        graphics.fill(contentX, contentY, contentX + contentWidth, contentY + contentHeight, 0xAA000000);
        graphics.outline(contentX, contentY, contentWidth, contentHeight, 0xFF555555);
        graphics.centeredText(this.font, Component.translatable("screen.sabi.sabi_machine.confirm_pawn"), this.width / 2, contentY + 10, 0xFFFFFFFF);

        ItemStack stack = this.quickPawnStack();
        if (stack.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("screen.sabi.sabi_machine.no_pawn_item"), this.width / 2, contentY + 38, 0xFFFFE7A3);
            return;
        }

        Row row = this.rowForItem(stack.getItem());
        graphics.item(stack, contentX + 18, contentY + 34);
        graphics.text(this.font, stack.getHoverName(), contentX + 42, contentY + 32, 0xFFE7FFE9, false);
        if (row == null) {
            graphics.text(this.font, Component.translatable("message.sabi.sabi_machine.item_not_allowed"), contentX + 42, contentY + 46, 0xFFFF9090, false);
        } else {
            graphics.text(this.font, Component.translatable("screen.sabi.sabi_machine.pawn_price", (long)row.pawnPrice * stack.getCount()), contentX + 42, contentY + 46, 0xFFFFE7A3, false);
        }
    }

    private void renderEmptyShulkerConfirmPage(GuiGraphicsExtractor graphics, int panelX, int panelY) {
        int contentX = panelX + 22;
        int contentY = panelY + 27;
        int contentWidth = IMAGE_WIDTH - 44;
        int contentHeight = 97;

        graphics.fill(contentX, contentY, contentX + contentWidth, contentY + contentHeight, 0xAA000000);
        graphics.outline(contentX, contentY, contentWidth, contentHeight, 0xFF555555);
        graphics.centeredText(this.font, Component.translatable("screen.sabi.sabi_machine.confirm_empty_shulker"), this.width / 2, contentY + 10, 0xFFFFFFFF);

        ItemStack stack = this.quickPawnStack();
        if (!stack.isEmpty()) {
            graphics.item(stack, contentX + 18, contentY + 34);
            graphics.text(this.font, stack.getHoverName(), contentX + 42, contentY + 32, 0xFFE7FFE9, false);
        }
        graphics.text(this.font, Component.translatable("screen.sabi.sabi_machine.empty_shulker_warning_1"), contentX + 42, contentY + 46, 0xFFFFE7A3, false);
        graphics.text(this.font, Component.translatable("screen.sabi.sabi_machine.empty_shulker_warning_2"), contentX + 42, contentY + 58, 0xFFFFE7A3, false);
    }

    private void renderDetailPage(GuiGraphicsExtractor graphics, int panelX, int panelY) {
        Row row = this.selectedRow();
        int contentX = panelX + 12;
        int contentY = panelY + 28;
        int contentWidth = IMAGE_WIDTH - 24;
        int contentHeight = 120;

        graphics.fill(contentX, contentY, contentX + contentWidth, contentY + contentHeight, 0xAA000000);
        graphics.outline(contentX, contentY, contentWidth, contentHeight, 0xFF555555);

        if (row == null) {
            graphics.centeredText(this.font, Component.translatable("screen.sabi.sabi_machine.no_selection"), this.width / 2, contentY + 50, 0xFFFFFFFF);
            return;
        }

        graphics.item(new ItemStack(row.item), contentX + 14, contentY + 12);
        graphics.text(this.font, row.name, contentX + 38, contentY + 10, 0xFFFFFFFF, false);
        graphics.text(this.font, Component.translatable("screen.sabi.sabi_machine.stored_count", row.storedCount), contentX + 38, contentY + 24, 0xFFE7FFE9, false);
        graphics.text(this.font, Component.translatable("screen.sabi.sabi_machine.pawn_price", row.pawnPrice), contentX + 14, contentY + 43, 0xFFFFE7A3, false);
        if (row.hasStoredItems()) {
            graphics.text(this.font, Component.translatable("screen.sabi.sabi_machine.redeem_price", row.redeemPrice), contentX + 14, contentY + 64, 0xFFFFE7A3, false);
        } else {
            graphics.text(this.font, Component.translatable("screen.sabi.sabi_machine.buy_price", row.buyUnitPrice()), contentX + 14, contentY + 64, 0xFFFFE7A3, false);
        }
        this.renderSecondLineBalance(graphics, row, contentX + 14, contentY + 85);
    }

    private void renderSecondLineBalance(GuiGraphicsExtractor graphics, Row row, int x, int y) {
        long account = SabiClientState.balance();
        int amount = this.parseAmount();
        if (amount <= 0) {
            graphics.text(this.font, Component.translatable("screen.sabi.sabi_machine.account", account), x, y, 0xFFB8FFD1, false);
            return;
        }

        long cost = row.hasStoredItems() ? (long)row.redeemPrice * amount : row.buyCost(amount);
        Component label = Component.translatable("screen.sabi.sabi_machine.cost_account");
        int labelColor = 0xFFB8FFD1;
        int costColor = cost > account ? 0xFFFF6060 : 0xFFFFE7A3;
        String costText = SabiAccount.format(cost);
        String accountText = "/" + SabiAccount.format(account);
        int labelWidth = this.font.width(label);
        int costWidth = this.font.width(costText);
        graphics.text(this.font, label, x, y, labelColor, false);
        graphics.text(this.font, costText, x + labelWidth, y, costColor, false);
        graphics.text(this.font, accountText, x + labelWidth + costWidth, y, 0xFFB8FFD1, false);
    }

    private void renderSearchIcon(GuiGraphicsExtractor graphics, int x, int y) {
        int outline = 0xFF5E4B8B;
        int lens = 0xFF5DCFF2;
        int highlight = 0xFFB8F7FF;
        int handle = 0xFF8E4A9F;
        int handleDark = 0xFF5B2F70;

        graphics.fill(x + 10, y + 10, x + 12, y + 12, handleDark);
        graphics.fill(x + 11, y + 11, x + 13, y + 13, handle);
        graphics.fill(x + 12, y + 12, x + 14, y + 14, handle);
        graphics.fill(x + 13, y + 13, x + 16, y + 16, handleDark);
        graphics.fill(x + 13, y + 13, x + 15, y + 15, handle);

        graphics.fill(x + 4, y, x + 10, y + 1, outline);
        graphics.fill(x + 2, y + 1, x + 4, y + 2, outline);
        graphics.fill(x + 10, y + 1, x + 12, y + 2, outline);
        graphics.fill(x + 1, y + 2, x + 2, y + 4, outline);
        graphics.fill(x + 12, y + 2, x + 13, y + 4, outline);
        graphics.fill(x, y + 4, x + 1, y + 8, outline);
        graphics.fill(x + 13, y + 4, x + 14, y + 8, outline);
        graphics.fill(x + 1, y + 8, x + 2, y + 10, outline);
        graphics.fill(x + 12, y + 8, x + 13, y + 10, outline);
        graphics.fill(x + 2, y + 10, x + 4, y + 11, outline);
        graphics.fill(x + 10, y + 10, x + 12, y + 11, outline);
        graphics.fill(x + 4, y + 11, x + 10, y + 12, outline);

        graphics.fill(x + 4, y + 1, x + 10, y + 2, lens);
        graphics.fill(x + 2, y + 2, x + 12, y + 4, lens);
        graphics.fill(x + 1, y + 4, x + 13, y + 8, lens);
        graphics.fill(x + 2, y + 8, x + 12, y + 10, lens);
        graphics.fill(x + 4, y + 10, x + 10, y + 11, lens);
        graphics.fill(x + 3, y + 2, x + 6, y + 3, highlight);
        graphics.fill(x + 2, y + 3, x + 4, y + 5, highlight);
    }

    private void renderSlotBackgrounds(GuiGraphicsExtractor graphics) {
        for (Slot slot : this.menu.slots) {
            if (slot.isActive()) {
                int x = this.leftPos + slot.x - 1;
                int y = this.topPos + slot.y - 1;
                graphics.fill(x, y, x + 18, y + 18, 0xFF8F8F8F);
                graphics.outline(x, y, 18, 18, 0xFF555555);
            }
        }
    }

    private void drawEmptyCell(GuiGraphicsExtractor graphics, int cellX, int cellY) {
        graphics.fill(cellX, cellY, cellX + CELL_SIZE, cellY + CELL_SIZE, 0xFF202020);
        graphics.outline(cellX, cellY, CELL_SIZE, CELL_SIZE, 0xFF444444);
    }

    private void drawItemCell(GuiGraphicsExtractor graphics, Row row, int cellX, int cellY, int mouseX, int mouseY) {
        boolean hasStoredItems = row.storedCount > 0;
        boolean selected = row.itemId.equals(this.selectedItemId);
        boolean hovered = mouseX >= cellX && mouseX < cellX + CELL_SIZE && mouseY >= cellY && mouseY < cellY + CELL_SIZE;

        graphics.fill(cellX, cellY, cellX + CELL_SIZE, cellY + CELL_SIZE, hasStoredItems ? 0xFF8F8F8F : 0xFF343434);
        graphics.outline(cellX, cellY, CELL_SIZE, CELL_SIZE, selected ? 0xFF39C37D : hovered ? 0xFFFFFFFF : 0xFF555555);
        graphics.item(new ItemStack(row.item), cellX + 1, cellY + 1);

        if (hasStoredItems) {
            String count = String.valueOf(Math.min(99, row.storedCount));
            graphics.text(this.font, count, cellX + CELL_SIZE + 1 - this.font.width(count), cellY + CELL_SIZE - 8, 0xFFFFFFFF, true);
        }
    }

    private void handleQuickPawnButton() {
        ItemStack stack = this.quickPawnStack();
        if (isShulkerBox(stack)) {
            if (hasShulkerBoxContents(stack)) {
                this.sendQuickPawnConfirm();
            } else {
                this.pageMode = PageMode.EMPTY_SHULKER_CONFIRM;
                this.updateWidgetStates();
            }
            return;
        }

        this.pageMode = PageMode.PAWN_CONFIRM;
        this.updateWidgetStates();
    }

    private void sendSecondLineAction() {
        Row selected = this.selectedRow();
        if (selected != null) {
            if (selected.hasStoredItems()) {
                ClientPacketDistributor.sendToServer(new SabiNetwork.PawnMachineRedeemPayload(this.menu.pos(), selected.itemId, this.parseAmount()));
            } else {
                ClientPacketDistributor.sendToServer(new SabiNetwork.PawnMachineBuyPayload(this.menu.pos(), selected.itemId, this.parseAmount()));
            }
            this.onClose();
        }
    }

    private void sendPawnConfirm() {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, SabiPawnMachineMenu.PAWN_CONFIRM_BUTTON);
            this.onClose();
        }
    }

    private void sendQuickPawnConfirm() {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, SabiPawnMachineMenu.QUICK_PAWN_CONFIRM_BUTTON);
        }
    }

    private void rebuildFilteredRows() {
        String query = this.searchBox == null ? "" : this.searchBox.getValue().toLowerCase(Locale.ROOT).trim();
        if (query.isEmpty()) {
            this.filteredRows = this.rows;
        } else {
            List<Row> result = new ArrayList<>();
            for (Row row : this.rows) {
                if (row.name.getString().toLowerCase(Locale.ROOT).contains(query) || row.itemId.toString().contains(query)) {
                    result.add(row);
                }
            }
            this.filteredRows = result;
        }
        this.clampScroll();
    }

    private void updateWidgetStates() {
        Row selected = this.selectedRow();
        boolean detailPage = this.pageMode == PageMode.DETAIL;
        boolean gridPage = this.pageMode == PageMode.GRID;
        boolean pawnConfirmPage = this.pageMode == PageMode.PAWN_CONFIRM;
        boolean emptyShulkerConfirmPage = this.pageMode == PageMode.EMPTY_SHULKER_CONFIRM;
        this.menu.setPawnInputMode(gridPage, detailPage);

        this.updateGridWidgetStates(gridPage);
        this.updateDetailWidgetStates(detailPage, selected);
        this.updateConfirmWidgetStates(pawnConfirmPage, emptyShulkerConfirmPage);
    }

    private void updateGridWidgetStates(boolean gridPage) {
        if (this.searchBox != null) {
            this.searchBox.visible = gridPage;
        }
        if (this.quickPawnButton != null) {
            this.quickPawnButton.visible = gridPage;
            this.quickPawnButton.active = !this.quickPawnStack().isEmpty();
        }
        if (this.doneButton != null) {
            this.doneButton.visible = gridPage;
        }
    }

    private void updateDetailWidgetStates(boolean detailPage, Row selected) {
        if (this.redeemAmountBox != null) {
            this.redeemAmountBox.visible = detailPage;
        }
        if (this.backButton != null) {
            this.backButton.visible = detailPage;
        }
        if (this.pawnConfirmButton != null) {
            Slot pawnSlot = this.menu.getSlot(SabiPawnMachineMenu.DETAIL_PAWN_INPUT_SLOT);
            this.pawnConfirmButton.visible = detailPage;
            this.pawnConfirmButton.active = selected != null && pawnSlot.hasItem() && pawnSlot.getItem().is(selected.item);
        }
        if (this.redeemButton != null) {
            int amount = this.parseAmount();
            this.redeemButton.visible = detailPage;
            this.redeemButton.setMessage(Component.translatable(selected != null && !selected.hasStoredItems() ? "button.sabi.buy" : "button.sabi.redeem"));
            this.redeemButton.active = selected != null && amount > 0 && amount <= new ItemStack(selected.item).getMaxStackSize()
                    && (selected.hasStoredItems()
                            ? selected.storedCount >= amount && SabiClientState.balance() >= (long)selected.redeemPrice * amount
                            : SabiClientState.balance() >= selected.buyCost(amount));
        }
    }

    private void updateConfirmWidgetStates(boolean pawnConfirmPage, boolean emptyShulkerConfirmPage) {
        if (this.quickPawnConfirmButton != null) {
            this.quickPawnConfirmButton.visible = pawnConfirmPage;
            this.quickPawnConfirmButton.active = this.rowForItem(this.quickPawnStack().getItem()) != null;
        }
        if (this.pawnCancelButton != null) {
            this.pawnCancelButton.visible = pawnConfirmPage;
        }
        if (this.emptyShulkerConfirmButton != null) {
            this.emptyShulkerConfirmButton.visible = emptyShulkerConfirmPage;
            this.emptyShulkerConfirmButton.active = isEmptyShulkerBox(this.quickPawnStack());
        }
        if (this.emptyShulkerCancelButton != null) {
            this.emptyShulkerCancelButton.visible = emptyShulkerConfirmPage;
        }
    }

    private boolean isEditBoxFocused() {
        return (this.searchBox != null && this.searchBox.isFocused()) || (this.redeemAmountBox != null && this.redeemAmountBox.isFocused());
    }

    private Row selectedRow() {
        if (this.selectedItemId == null) {
            return null;
        }
        for (Row row : this.rows) {
            if (row.itemId.equals(this.selectedItemId)) {
                return row;
            }
        }
        return null;
    }

    private ItemStack quickPawnStack() {
        return this.menu.getSlot(SabiPawnMachineMenu.QUICK_PAWN_INPUT_SLOT).getItem();
    }

    private Row rowForItem(Item item) {
        if (item == null) {
            return null;
        }
        for (Row row : this.rows) {
            if (row.item == item) {
                return row;
            }
        }
        return null;
    }

    private int gridIndexAt(double mouseX, double mouseY) {
        int gridX = this.gridX();
        int gridY = this.gridY();
        if (!this.isInGridBounds(mouseX, mouseY)) {
            return -1;
        }

        int relativeX = (int)(mouseX - gridX);
        int relativeY = (int)(mouseY - gridY);
        int column = relativeX / CELL_SIZE;
        int row = relativeY / CELL_SIZE;
        if (column >= GRID_COLUMNS || row >= GRID_VISIBLE_ROWS) {
            return -1;
        }
        return this.scroll * GRID_COLUMNS + row * GRID_COLUMNS + column;
    }

    private boolean isInGridBounds(double mouseX, double mouseY) {
        int gridX = this.gridX();
        int gridY = this.gridY();
        return mouseX >= gridX && mouseX < gridX + GRID_WIDTH && mouseY >= gridY && mouseY < gridY + GRID_HEIGHT;
    }

    private void clampScroll() {
        int totalRows = (this.filteredRows.size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
        int maxScroll = Math.max(0, totalRows - GRID_VISIBLE_ROWS);
        if (this.scroll < 0) {
            this.scroll = 0;
        } else if (this.scroll > maxScroll) {
            this.scroll = maxScroll;
        }
    }

    private int parseAmount() {
        if (this.redeemAmountBox == null || this.redeemAmountBox.getValue().isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(this.redeemAmountBox.getValue()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void normalizeAmountBox() {
        if (this.redeemAmountBox == null) {
            return;
        }
        Row selected = this.selectedRow();
        int max = selected == null ? 64 : new ItemStack(selected.item).getMaxStackSize();
        String value = this.redeemAmountBox.getValue();
        StringBuilder digits = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character >= '0' && character <= '9') {
                digits.append(character);
            }
        }
        int amount = 1;
        if (digits.isEmpty()) {
            if (!value.isEmpty()) {
                this.redeemAmountBox.setValue("");
            }
            return;
        }
        try {
            amount = Integer.parseInt(digits.toString());
        } catch (NumberFormatException ignored) {
            amount = 1;
        }
        amount = Math.max(1, Math.min(max, amount));
        String normalized = String.valueOf(amount);
        if (!value.equals(normalized)) {
            this.redeemAmountBox.setValue(normalized);
        }
    }

    private int gridX() {
        return this.leftPos + (IMAGE_WIDTH - GRID_WIDTH) / 2;
    }

    private int gridY() {
        return this.topPos + 54;
    }

    private static boolean isShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private static boolean isEmptyShulkerBox(ItemStack stack) {
        return isShulkerBox(stack) && !hasShulkerBoxContents(stack);
    }

    private static boolean hasShulkerBoxContents(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().findAny().isPresent();
    }

    private enum PageMode {
        GRID,
        DETAIL,
        PAWN_CONFIRM,
        EMPTY_SHULKER_CONFIRM
    }

    private record Row(Identifier itemId, Item item, int storedCount, int pawnPrice, int redeemPrice, int originalOrder, Component name) {
        Row(Identifier itemId, Item item, int storedCount, int pawnPrice, int redeemPrice, int originalOrder) {
            this(itemId, item, storedCount, pawnPrice, redeemPrice, originalOrder, displayName(item));
        }

        private static Component displayName(Item item) {
            if (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION || item == Items.TIPPED_ARROW) {
                return Component.translatable(item.getDescriptionId());
            }
            return new ItemStack(item).getHoverName();
        }

        private boolean hasStoredItems() {
            return this.storedCount > 0;
        }

        private long buyUnitPrice() {
            return Math.max(0L, this.pawnPrice) * 4L;
        }

        private long buyCost(int amount) {
            return this.buyUnitPrice() * amount;
        }
    }
}
