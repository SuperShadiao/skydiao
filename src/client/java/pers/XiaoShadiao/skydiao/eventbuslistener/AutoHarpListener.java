package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Map;

public class AutoHarpListener extends AbstractListener {

    private int clickLogger;
    private Map.Entry<Integer, Boolean>[][] keys = new Map.Entry[9][6];

    @Override
    public String getListenerName() {
        return "监听器_SkbAutoHarp";
    }

    @Override
    public void registerListeners() {
        ScreenEvents.AFTER_INIT.register(this::onGuiOpen);
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientEndTick);
    }

    private Integer nextClick = null;
    private int containerId;

    private void onClientEndTick(Minecraft mc) {
        if(mc.level != null && mc.player != null) {
            if (mc.screen instanceof ContainerScreen containerScreen) {
                ChestMenu menu = containerScreen.getMenu();
                String chestName = ToolList.getInstance().deleteColorCode(containerScreen.getTitle().getString());
                if ((chestName.contains("Harp -"))) {
                    int i = 0;
                    Map.Entry<Integer, Boolean>[][] keyss = new Map.Entry[9][6];
                    for (Slot slot : menu.slots) {
                        if(slot != null && slot.hasItem() && slot.container != mc.player.getInventory()) {
                            Item item = slot.getItem().getItem();
                            if(item instanceof BlockItem && (/*((ItemBlock) item).block.equals(Blocks.quartz_block) || */((BlockItem) item).getBlock().getDescriptionId().endsWith("_wool"))) {
                                // keyss[i % 9][i / 9] = new AbstractMap.SimpleEntry<>(slot.index, (i / 9) <= 3);
                                keyss[i % 9][i / 9] = Map.entry(slot.index, (i / 9) <= 3);
                            } else {
                                // keyss[i % 9][i / 9] = new AbstractMap.SimpleEntry<>(slot.index, false);
                                keyss[i % 9][i / 9] = Map.entry(slot.index, false);
                            }
                        } else if(slot != null && slot.container != mc.player.getInventory()) {
                            keyss[i % 9][i / 9] = new AbstractMap.SimpleEntry<>(slot.index, false);
                        }
                        i++;
                    }

                    if(!Arrays.deepEquals(keys, keyss)) {

                        for(int j = 0; j < 9; j++) {
                            if(keys[j][3] != null && keyss[j][3] != null && keys[j][3].getValue() == true) {
                                nextClick = keys[j][4].getKey();
                                containerId = menu.containerId;
                            }
                        }

                        keys = keyss;
                    }
                }
            }
        }
    }

    public void onGuiOpen(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        if (!ConfigManager.autoHarp.getValue() || !(screen instanceof ContainerScreen containerScreen)) return;
        ChestMenu menu = containerScreen.getMenu();
        Container container = menu.getContainer();
        if (container instanceof SimpleContainer simpleContainer) {
            String chestName = ToolList.getInstance().deleteColorCode(containerScreen.getTitle().getString());
            if (chestName.contains("Harp -")) {
                clickLogger = 0;
                nextClick = null;
                keys = new Map.Entry[9][6];
                ScreenEvents.afterExtract(screen).register(this::onGuiDraw);
            }
        }
    }

    public void onGuiDraw(Screen screen, GuiGraphicsExtractor drawContext, int mouseX, int mouseY, float tickDelta) {
        if(nextClick != null) {
            Integer slotId = nextClick;

            mc.gameMode.handleContainerInput(containerId, slotId, 0, ContainerInput.PICKUP, mc.player);
            clickLogger++;
            System.out.println("Click! " + clickLogger + " " + slotId);
            nextClick = null;
        }
    }

}
