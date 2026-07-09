package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import pers.XiaoShadiao.skydiao.utils.ToolList;

public class AutoLoadoutListener extends AbstractListener {

    @Override
    public String getListenerName() {
        return "AutoLoadoutListener";
    }

    @Override
    public void registerListeners() {
        ScreenEvents.AFTER_INIT.register(this::onLoadoutGuiOpen);
    }

    public static int loadout = 0;

    public void onLoadoutGuiOpen(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        if (!(screen instanceof ContainerScreen containerScreen)) return;
        Container container = containerScreen.getMenu().getContainer();
        if (container instanceof SimpleContainer) {
            String chestName = ToolList.getInstance().deleteColorCode(containerScreen.getTitle().getString());
            if (chestName.contains("Loadouts")) ScreenEvents.afterExtract(screen).register(this::onLoadoutGuiDraw);
        }
    }

    public void onLoadoutGuiDraw(Screen screen, GuiGraphicsExtractor drawContext, int mouseX, int mouseY, float tickDelta) {
        if (loadout == 0) return;

        if (!(screen instanceof ContainerScreen)) return;
        ChestMenu menu = ((ContainerScreen) screen).getMenu();
        Container container = menu.getContainer();
        if (!(container instanceof SimpleContainer)) return;

        int page = loadout / 12;
        int row = (loadout % 12 + 2) / 3;
        int col = (loadout % 12 + 2) % 3 + 1;
        int slot = 4 + (row * 9) + col;
        loadout = 0;

        ToolList.addThreadedTask(() -> {
            Thread.sleep(1000 + ToolList.getInstance().random.nextInt(150));
            for (int i = 0; i < page; i++) {
                mc.gameMode.handleContainerInput(menu.containerId, 44, 0, ContainerInput.PICKUP, mc.player);
                Thread.sleep(800 + ToolList.getInstance().random.nextInt(150));
            }
            mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, mc.player);
            Thread.sleep(800 + ToolList.getInstance().random.nextInt(150));
            mc.screen.onClose();
            return null;
        });
    }
}

// index table
// 14 15 16 lastp
// 23 24 25
// 32 33 34
// 41 42 43 nextp