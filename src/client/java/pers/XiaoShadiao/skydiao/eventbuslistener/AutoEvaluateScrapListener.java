package pers.XiaoShadiao.skydiao.eventbuslistener;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;
import pers.XiaoShadiao.skydiao.utils.scrapsolver.FossilSolver;
import pers.XiaoShadiao.skydiao.utils.scrapsolver.FossilTile;

import java.util.List;

public class AutoEvaluateScrapListener extends AbstractListener {

    @Override
    public String getListenerName() {
        return "AutoEvaluateScrapListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
        ScreenEvents.AFTER_INIT.register(this::afterScreenInit);
    }

    private void afterScreenInit(Minecraft mc, Screen screen, int x, int y) {
        if(!ConfigManager.autoEvaluateFossils.getValue()) return;
        if(screen instanceof ContainerScreen) {
            lastClickTime = System.currentTimeMillis() + 200;
        }
    }

    private long lastClickTime = 0;
    private int lastDirtCount;

    private void onClientTick(Minecraft mc) {

        if(!ConfigManager.autoEvaluateFossils.getValue()) return;
        if(mc.player == null || mc.level == null || mc.gameMode == null) return;

        if(mc.screen instanceof ContainerScreen containerScreen && ToolList.getInstance().deleteColorCode(containerScreen.getTitle().getString()).equals("Fossil Excavator")) {
            IntSet dirts = new IntOpenHashSet();
            IntSet firstClickDirts = new IntOpenHashSet();
            IntSet fossils = new IntOpenHashSet();

            for (Slot slot : containerScreen.getMenu().slots) {
                if(slot.container != containerScreen.getMenu().getContainer()) continue;

                List<Component> lines = slot.getItem().getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
                String itemName = ToolList.getInstance().deleteColorCode(slot.getItem().getHoverName().getString());
                boolean isDirt = itemName.equals("Dirt");
                boolean isFossil = itemName.equals("Fossil");

                boolean isStart = itemName.equals("Start Excavator") && slot.getItem().getItem() == Items.GREEN_TERRACOTTA;

                if(isDirt || isStart) {
                    if(slot.getItem().getItem() == Items.LIME_STAINED_GLASS_PANE) {
                        firstClickDirts.add(slot.index);
                    }
                    dirts.add(slot.index);
                } else if(isFossil) {
                    fossils.add(slot.index);
                } else continue;

                for (Component line : lines) {
                    String lineNoColor = ToolList.getInstance().deleteColorCode(line.getString());
                    if(lineNoColor.equals("Chisel Charges Remaining: 0")) {
                        closeScreenAndReRun();
                        return;
                    }
                }
            }

            if(lastDirtCount - dirts.size() > 3) {
                closeScreenAndReRun();
                lastDirtCount = 0;
                return;
            }
            lastDirtCount = dirts.size();

            if(System.currentTimeMillis() - lastClickTime < 200 + ToolList.getInstance().random.nextInt(100)) return;

            lastClickTime = System.currentTimeMillis();

            FossilTile bestTile = FossilSolver.findBestTile(fossils, dirts);
            if(bestTile != null) {
                mc.gameMode.handleContainerInput(containerScreen.getMenu().containerId, bestTile.toSlotIndex(), 0, ContainerInput.PICKUP, mc.player);
            } else if(!dirts.isEmpty()) {
                IntList random = new IntArrayList(dirts);
                int randomDirt = firstClickDirts.isEmpty() ? random.getInt(ToolList.getInstance().random.nextInt(random.size())) : firstClickDirts.iterator().nextInt();
                mc.gameMode.handleContainerInput(containerScreen.getMenu().containerId, randomDirt, 0, ContainerInput.PICKUP, mc.player);
            }
        }

    }

    private void closeScreenAndReRun() {
        if(mc.screen != null) mc.screen.onClose();
        if(ConfigManager.autoEvaluateFossilsAutoRestart.getValue()) {
            ToolList.addThreadedTask(() -> {
                Thread.sleep(1500);
                InputSimulator.singleRightClick();
                return null;
            });
        }
    }

}
