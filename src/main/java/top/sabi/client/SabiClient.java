package top.sabi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ScreenEvent;
import top.sabi.Sabi;
import top.sabi.SabiClientState;

public final class SabiClient {
    private static final int BOX_WIDTH = 60;
    private static final int BOX_HEIGHT = 18;

    private SabiClient() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(SabiClient::registerMenuScreens);
        NeoForge.EVENT_BUS.addListener(SabiClient::onScreenRender);
        NeoForge.EVENT_BUS.addListener(SabiClient::onScreenMousePressed);
    }

    private static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(Sabi.PAWN_MACHINE_MENU.get(), SabiPawnMachineScreen::new);
    }

    public static void openPawnMachine(top.sabi.SabiNetwork.PawnMachinePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SabiPawnMachineScreen pawnMachineScreen && pawnMachineScreen.sameMachine(payload.pos())) {
            pawnMachineScreen.update(payload);
        }
    }

    public static void showPawnMachineNotice(top.sabi.SabiNetwork.PawnMachineNoticePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SabiPawnMachineScreen pawnMachineScreen && pawnMachineScreen.sameMachine(payload.pos())) {
            pawnMachineScreen.showNotice(payload.notice(), payload.containerKind());
        }
    }

    private static void onScreenMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen inventoryScreen) || event.getMouseButtonEvent().button() != 0) {
            return;
        }

        if (isInside(inventoryScreen, (int)event.getMouseX(), (int)event.getMouseY())) {
            Minecraft.getInstance().setScreen(new SabiAccountScreen());
            event.setCanceled(true);
        }
    }

    private static void onScreenRender(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof InventoryScreen inventoryScreen)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        int x = accountX(inventoryScreen);
        int y = accountY(inventoryScreen);
        graphics.fill(x, y, x + BOX_WIDTH, y + BOX_HEIGHT, 0xAA101014);
        graphics.outline(x, y, BOX_WIDTH, BOX_HEIGHT, 0xFF39C37D);
        graphics.text(minecraft.font, Component.literal("S " + compact(SabiClientState.balance())), x + 5, y + 5, 0xFFE7FFE9, false);

        if (isInside(inventoryScreen, event.getMouseX(), event.getMouseY())) {
            graphics.text(minecraft.font, Component.translatable("tooltip.sabi.account_balance", top.sabi.SabiAccount.format(SabiClientState.balance())), x, y - 12, 0xFFE7FFE9, false);
        }
    }

    private static boolean isInside(InventoryScreen screen, int mouseX, int mouseY) {
        int x = accountX(screen);
        int y = accountY(screen);
        return mouseX >= x && mouseX < x + BOX_WIDTH && mouseY >= y && mouseY < y + BOX_HEIGHT;
    }

    private static int accountX(InventoryScreen screen) {
        return screen.getLeftPos() + 8;
    }

    private static int accountY(InventoryScreen screen) {
        return screen.getTopPos() - 24;
    }

    private static String compact(long value) {
        if (value >= 1_000_000_000L) {
            return value / 1_000_000_000L + "B";
        }
        if (value >= 1_000_000L) {
            return value / 1_000_000L + "M";
        }
        if (value >= 1_000L) {
            return value / 1_000L + "K";
        }
        return Long.toString(value);
    }
}
