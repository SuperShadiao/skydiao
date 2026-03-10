package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.*;

/*
 * 好的抄写, 来自GreenCat ((
 */
public class AutoEnchantmentTableGameListener extends AbstractListener {
    private ExperimentType currentExperiment = ExperimentType.NONE;
    private boolean hasAdded = false;
    private int clicks = 0;
    private long lastClickTime = 0L;
    private final List<Map.Entry<Integer, String>> chronomatronOrder = new ArrayList<>(28);
    private int lastAdded = 0;
    private final HashMap<Integer,Integer> ultrasequencerOrder = new HashMap<>();

    private String chronomatronOrderFirstName = "";

    private Runnable delayedTask = null;

    public void onGuiOpen(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        currentExperiment = ExperimentType.NONE;
        hasAdded = false;
        chronomatronOrder.clear();
        lastAdded = 0;
        ultrasequencerOrder.clear();
        if (!ConfigManager.autoEnchantTableGame.getValue() || !(screen instanceof ContainerScreen)) return;
        ContainerScreen containerScreen = ((ContainerScreen) screen);
        ChestMenu menu = containerScreen.getMenu();
        Container container = menu.getContainer();
        if (container instanceof SimpleContainer simpleContainer) {
            String chestName = ToolList.getInstance().deleteColorCode(containerScreen.getTitle().getString());
            if (chestName.contains("Chronomatron") && !chestName.contains("Sta")) {
                currentExperiment = ExperimentType.CHRONOMATRON;
            } else if (chestName.contains("Ultrasequencer") && !chestName.contains("Sta")) {
                currentExperiment = ExperimentType.ULTRASEQUENCER;
            } else if (chestName.startsWith("Superpairs (")) {
                currentExperiment = ExperimentType.SUPERPAIRS;
            }
            if(currentExperiment != ExperimentType.NONE) ScreenEvents.afterRender(screen).register(this::onGuiDraw);
        }
    }

    private long pickupItemTestTime = System.currentTimeMillis();

    public void onGuiDraw(Screen screen, GuiGraphics drawContext, int mouseX, int mouseY, float tickDelta) {
        // log.info("BackgroundDrawnEvent");
        // log.info("=============[C]==============");
        // log.info(!工具列表.getInstance().事件触发器.skyblock());
        // log.info(!(event.gui instanceof GuiChest));
        // log.info(Config管理.getSBAutoPlayEnchant() != 1);
        // log.info("=============[C]==============");
        if (!(screen instanceof ContainerScreen) || false) return;
        ChestMenu menu = ((ContainerScreen) screen).getMenu();
        Container container = menu.getContainer();
        if (container instanceof SimpleContainer simpleContainer) {

            if(false) {
                if(System.currentTimeMillis() - pickupItemTestTime > 1000) {
                    pickupItemTestTime = System.currentTimeMillis();
                    Optional<Slot> slot = menu.slots.stream().filter(i -> !i.getItem().isEmpty()).findAny();
                    slot.ifPresent(slot1 -> mc.gameMode.handleInventoryMouseClick(menu.containerId, slot1.index, 0, ClickType.PICKUP, mc.player));
                }
                return;
            }

            switch (currentExperiment) {
                case CHRONOMATRON:
                    if (!isStackNull(menu.slots,49) && menu.slots.get(49).getItem().getItem() == Item.byBlock(Blocks.GLOWSTONE) && !isStackNull(menu.slots,lastAdded) &&
                            !ToolList.hasGlint(menu.slots.get(lastAdded).getItem())
                    ) {
                        hasAdded = false;
                    }
                    if (!hasAdded && !isStackNull(menu.slots,49) && menu.slots.get(49).getItem().getItem() == Items.CLOCK) {
                        if(delayedTask == null) delayedTask = () -> {
                            Optional<Slot> optional = menu.slots.stream().filter(it -> it.index >= 10 && it.index <= 43).filter(item -> ToolList.hasGlint(item.getItem())).min((a, b) -> {
                                if (chronomatronOrderFirstName.isEmpty()) return 0;
                                String aName = ToolList.getInstance().deleteColorCode(a.getItem().getDisplayName().getString());
                                String bName = ToolList.getInstance().deleteColorCode(b.getItem().getDisplayName().getString());
                                if (!aName.equals(chronomatronOrderFirstName) && bName.equals(chronomatronOrderFirstName))
                                    return -1;
                                if (aName.equals(chronomatronOrderFirstName) && !bName.equals(chronomatronOrderFirstName))
                                    return 1;
                                return 0;
                            }); // 排序以解决一个神秘bug (有时会错误把第一个遇到的栏位添加到最后, 即便它不是目标栏位)
                            if (optional.isPresent()) {
                                Slot slot = optional.get();
                                chronomatronOrder.add(new AbstractMap.SimpleEntry<>(slot.index, ToolList.getInstance().deleteColorCode(slot.getItem().getDisplayName().getString())));
                                logger.info(chronomatronOrder);
                                if(chronomatronOrder.size() == 1) {
                                    chronomatronOrderFirstName = chronomatronOrder.get(0).getValue();
                                }
                                lastAdded = slot.index;
                                hasAdded = true;
                                clicks = 0;
                            }
                        };
                    }

                    if (hasAdded && !isStackNull(menu.slots,49) && menu.slots.get(49).getItem().getItem() == Items.CLOCK && chronomatronOrder.size() > clicks &&
                            System.currentTimeMillis() - lastClickTime > ToolList.getInstance().random.nextInt(1200) + 300
                    ) {
//                        mc.windowClick(
//                                mc.thePlayer.openContainer.windowId,
//                                chronomatronOrder.get(clicks).getKey(),
//                                0,
//                                0,
//                                mc.thePlayer
//                        );
                        mc.gameMode.handleInventoryMouseClick(menu.containerId, chronomatronOrder.get(clicks).getKey(), 0, ClickType.PICKUP, mc.player);
                        lastClickTime = System.currentTimeMillis();
                        clicks++;
                    }
                    break;
                case ULTRASEQUENCER:
                    if (!isStackNull(menu.slots,49) && menu.slots.get(49).getItem().getItem() == Items.CLOCK) {
                        hasAdded = false;
                    }
                    if (!hasAdded && !isStackNull(menu.slots,49) && menu.slots.get(49).getItem().getItem() == Item.byBlock(Blocks.GLOWSTONE)) {
                        if (menu.slots.get(44).getItem().isEmpty()) return;
                        if(delayedTask == null) delayedTask = () -> {
                            ultrasequencerOrder.clear();
                            menu.slots.stream().filter(it -> it.index >= 9 && it.index <= 44).forEach(this::setUltraSequencerOrder);
                            hasAdded = true;
                            clicks = 0;
                            logger.info(ultrasequencerOrder);
                        };
                    }
                    if (!isStackNull(menu.slots,49) && menu.slots.get(49).getItem().getItem() == Items.CLOCK && ultrasequencerOrder.containsKey(clicks) &&
                            System.currentTimeMillis() - lastClickTime > ToolList.getInstance().random.nextInt(1200) + 300
                    ) {
                        Integer slot = ultrasequencerOrder.get(clicks);
                        if (slot != null) {
//                            mc.playerController.windowClick(
//                                    mc.thePlayer.openContainer.windowId,
//                                    slot, 0, 0, mc.thePlayer
//                            );
                            mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, mc.player);
                        }
                        lastClickTime = System.currentTimeMillis();
                        clicks++;
                    }
                    break;
                default:
                    break;

            }
        }
    }

    public void setUltraSequencerOrder(Slot slot) {
        if (slot.getItem() != ItemStack.EMPTY && (slot.getItem().getCount() > 1 || slot.getItem().getItem() == Items.BONE_MEAL)) {
            ultrasequencerOrder.put(slot.getItem().getCount() - 1,slot.index);
        }
    }

    private boolean isStackNull(List<Slot> l, int index) {
        return l.get(index) == null || l.get(index).getItem() == ItemStack.EMPTY;
    }

    @Override
    public String getListenerName() {
        return "AutoEnchantmentTableGameListener";
    }

    @Override
    protected void registerListeners() {
        ScreenEvents.AFTER_INIT.register(this::onGuiOpen);
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
    }

    private int tickPassed;
    private void onClientTick(Minecraft mc) {
        if(delayedTask != null) {
            tickPassed++;
            if(tickPassed > 1) { // 确保所有物品更新完毕
                delayedTask.run();
                delayedTask = null;
                tickPassed = 0;
            }
        } else tickPassed = 0;
    }

    private <T> T printThis(T obj) {
        System.out.println(obj);
        return obj;
    }

    enum ExperimentType {
        CHRONOMATRON,
        ULTRASEQUENCER,
        SUPERPAIRS,
        NONE
    }
}