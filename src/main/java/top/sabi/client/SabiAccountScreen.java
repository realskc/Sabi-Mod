package top.sabi.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import top.sabi.SabiAccount;
import top.sabi.SabiClientState;
import top.sabi.SabiNetwork;

public class SabiAccountScreen extends Screen {
    private EditBox amountBox;

    protected SabiAccountScreen() {
        super(Component.translatable("screen.sabi.account"));
    }

    @Override
    protected void init() {
        int panelX = this.width / 2 - 100;
        int panelY = this.height / 2 - 58;

        this.amountBox = new EditBox(this.font, panelX + 20, panelY + 52, 160, 20, Component.translatable("screen.sabi.withdraw_amount"));
        this.amountBox.setMaxLength(18);
        this.amountBox.setValue("64");
        this.addRenderableWidget(this.amountBox);

        this.addRenderableWidget(Button.builder(Component.translatable("button.sabi.withdraw"), button -> withdraw())
                .bounds(panelX + 20, panelY + 78, 76, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("button.sabi.deposit_all"), button ->
                ClientPlayNetworking.send(new SabiNetwork.AccountActionPayload(SabiNetwork.AccountAction.DEPOSIT_ALL, 0))
        ).bounds(panelX + 104, panelY + 78, 76, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(panelX + 62, panelY + 104, 76, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int panelX = this.width / 2 - 100;
        int panelY = this.height / 2 - 58;
        graphics.fill(panelX, panelY, panelX + 200, panelY + 132, 0xDD101014);
        graphics.outline(panelX, panelY, 200, 132, 0xFF39C37D);
        graphics.centeredText(this.font, this.title, this.width / 2, panelY + 12, 0xFFE7FFE9);
        graphics.text(this.font, Component.translatable("screen.sabi.current_balance", SabiAccount.format(SabiClientState.balance())), panelX + 20, panelY + 32, 0xFFFFFFFF, false);
        graphics.text(this.font, Component.translatable("screen.sabi.withdraw_amount"), panelX + 20, panelY + 43, 0xFFB8FFD1, false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void withdraw() {
        long amount;
        try {
            amount = Long.parseLong(this.amountBox.getValue());
        } catch (NumberFormatException ignored) {
            return;
        }

        if (amount > 0) {
            ClientPlayNetworking.send(new SabiNetwork.AccountActionPayload(SabiNetwork.AccountAction.WITHDRAW, amount));
        }
    }
}
