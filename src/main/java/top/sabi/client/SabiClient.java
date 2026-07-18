package top.sabi.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import top.sabi.Sabi;
import top.sabi.SabiClientState;
import top.sabi.SabiNetwork;

public final class SabiClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(Sabi.PAWN_MACHINE_MENU, SabiPawnMachineScreen::new);
        SabiNetwork.registerClient();
        ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
            if (screen instanceof InventoryScreen) {
                int x = (width - 176) / 2 + 8;
                int y = (height - 166) / 2 - 24;
                Screens.getWidgets(screen).add(Button.builder(
                        Component.literal("S " + compact(SabiClientState.balance())),
                        button -> minecraft.gui.setScreen(new SabiAccountScreen()))
                        .bounds(x, y, 60, 18).build());
            }
        });
    }

    public static void openPawnMachine(SabiNetwork.PawnMachinePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof SabiPawnMachineScreen screen && screen.sameMachine(payload.pos())) screen.update(payload);
    }

    public static void showPawnMachineNotice(SabiNetwork.PawnMachineNoticePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof SabiPawnMachineScreen screen && screen.sameMachine(payload.pos())) {
            screen.showNotice(payload.notice(), payload.containerKind());
        }
    }

    private static String compact(long value) {
        if (value >= 1_000_000_000L) return value / 1_000_000_000L + "B";
        if (value >= 1_000_000L) return value / 1_000_000L + "M";
        if (value >= 1_000L) return value / 1_000L + "K";
        return Long.toString(value);
    }
}
