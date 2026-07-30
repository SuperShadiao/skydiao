package pers.XiaoShadiao.skydiao.eventbuslistener;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import pers.XiaoShadiao.skydiao.utils.PageSwitchCallback;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.ArrayList;
import java.util.List;

public class FastClearMiningStash extends AbstractListener {

    private PageSwitchCallback pageSwitchCallback;
    private ToolList.ThreadedTask<?> task;

    @Override
    public String getListenerName() {
        return "FastClearMiningSack";
    }

    @Override
    public void registerListeners() {
        ScreenEvents.AFTER_INIT.register(this::afterScreenInit);
    }

    private void afterScreenInit(Minecraft minecraft, Screen screen, int i, int i1) {
        if(pageSwitchCallback != null) {
            pageSwitchCallback.setScreen(screen);
            pageSwitchCallback = null;
        }
    }


    private List<String> getStashItemIdsAndPickup() throws InterruptedException {
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §e如果任务陷入死循环, 请在viewstash页面拼手速手动关闭页面即可停止"));
        ToolList.sendChatMessage("/viewstash material");
        PageSwitchCallback pageSwitchCallback = this.pageSwitchCallback = new PageSwitchCallback();
        if (!(pageSwitchCallback.getScreen(1500) instanceof ContainerScreen containerScreen)) return List.of();
        ChestMenu menu0 = containerScreen.getMenu();
        Container container0 = menu0.getContainer();
        if (!(container0 instanceof SimpleContainer)) return List.of();

        Thread.sleep(300);

        List<String> list = new ArrayList<>();
        IntList clickTargets = new IntArrayList();
        for (int i = 0; i < menu0.slots.size(); i++) {
            Slot slot = menu0.slots.get(i);
            if(slot.container != container0) continue;
            ItemStack item = slot.getItem();
            String id = ToolList.getInstance().tryGetSkyblockItemId(item);
            if (!id.isEmpty()) {
                list.add(id);
            } else {
                if (item.getItem() == Items.EMERALD || item.getItem() == Items.CHEST) {
                    clickTargets.add(i);
                }
            }
        }
        for (int i = clickTargets.size() - 1; i >= 0; i--) {
            int finalSlot = clickTargets.getInt(i);

            if(mc.screen != containerScreen) return List.of();
            mc.execute(() -> {
                if (mc.screen == containerScreen && mc.player != null && mc.gameMode != null) {
                    mc.gameMode.handleContainerInput(menu0.containerId, finalSlot, 0, ContainerInput.PICKUP, mc.player);
                }
            });
            Thread.sleep(400);
        }
        Thread.sleep(300);
        return list;
    }

    private boolean executeSupercraft(List<String> ids) throws InterruptedException {
        for (String id : ids) {
            String enchantedItemId = handleId(id);
            ToolList.sendChatMessage("/viewrecipe " + enchantedItemId);

            PageSwitchCallback pageSwitchCallback = this.pageSwitchCallback = new PageSwitchCallback();
            if (!(pageSwitchCallback.getScreen(1500) instanceof ContainerScreen containerScreen)) continue;
            ChestMenu menu0 = containerScreen.getMenu();
            Container container0 = menu0.getContainer();
            if (!(container0 instanceof SimpleContainer)) return false;
            Thread.sleep(300);
            for (int i = 0; i < menu0.slots.size(); i++) {
                Slot slot = menu0.slots.get(i);
                if(slot.container != container0) continue;
                ItemStack item = slot.getItem();
                if(ToolList.getInstance().deleteColorCode(item.getHoverName().getString()).equals("Supercraft")) {
                    int finalSlot = i;
                    mc.execute(() -> {
                        if (mc.screen == containerScreen && mc.player != null && mc.gameMode != null) {
                            mc.gameMode.handleContainerInput(menu0.containerId, finalSlot, 0, ContainerInput.QUICK_MOVE, mc.player);
                        }
                    });
                    Thread.sleep(300);
                    mc.execute(() -> {
                        if (mc.screen == containerScreen && mc.player != null && mc.gameMode != null) {
                            mc.gameMode.handleContainerInput(menu0.containerId, finalSlot, 0, ContainerInput.PICKUP, mc.player);
                        }
                    });
                    Thread.sleep(300);
                    if(mc.screen == containerScreen) mc.execute(() -> mc.screen.onClose()); else return false;
                    Thread.sleep(600);
                }
            }
        }
        return true;
    }

    public void startClearTask() {
        if(task != null && !task.future.isDone()) return;
        task = ToolList.addThreadedTask(() -> {
            while (true) {
                List<String> ids = getStashItemIdsAndPickup();
                if (ids.isEmpty()) return null;
                if (!executeSupercraft(ids)) return null;
            }
        });
    }

    // private List<String> gemstoneRarity = List.of("ROUGH_", "FLAWED_", "FINE_");

    private String handleId(String id) {
        boolean enchanted = true;
        if(id.endsWith("_INGOT")) {
            id = id.substring(0, id.length() - 6);
        }
        if(id.endsWith("_ORE")) {
            id = id.substring(0, id.length() - 4);
        }
        if(id.startsWith("ROUGH_")) {
            id = "FLAWED_" + id.substring(6);
            enchanted = false;
        }
        if(id.startsWith("FLAWED_")) {
            id = "FINE_" + id.substring(7);
            enchanted = false;
        }

        if(enchanted) id = ("enchanted_" + id);
        return id.toUpperCase();
    }

}
