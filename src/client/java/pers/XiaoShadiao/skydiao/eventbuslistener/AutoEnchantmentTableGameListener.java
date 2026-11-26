package pers.XiaoShadiao.skydiao.eventbuslistener;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.*;

/*
 * 好的抄写, 来自GreenCat ((
 * https://github.com/ItzGreenCat/Antimony-SkyBlock/blob/master/src/main/java/com/greencat/antimony/common/function/ExperimentsBot.java
 */
public class AutoEnchantmentTableGameListener extends AbstractListener {
    private ExperimentType currentExperiment = ExperimentType.NONE;
    private boolean hasAdded = false;
    private int clicks = 0;
    private long lastClickTime = 0L;
    private final List<Map.Entry<Integer, String>> chronomatronOrder = new ArrayList<>(28);
    private int lastAdded = 0;
    private final HashMap<Integer,Integer> ultrasequencerOrder = new HashMap<>();

    private final Int2ObjectMap<ItemStackPack> knownItems = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<ItemStackPack> knownButHiddenItems = new Int2ObjectOpenHashMap<>();

    public record ItemStackPack(ItemStack itemStack) {

        public int hashCode() {
            return itemStack.getItem().hashCode() ^ itemStack.getHoverName().getString().hashCode();
        }

        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            ItemStackPack that = (ItemStackPack) obj;
            return itemStack.getItem().equals(that.itemStack.getItem()) && itemStack.getHoverName().getString().equals(that.itemStack.getHoverName().getString());
        }

    }

    private String chronomatronOrderFirstName = "";

    private Runnable delayedTask = null;

    public void onGuiOpen(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        currentExperiment = ExperimentType.NONE;
        hasAdded = false;
        chronomatronOrder.clear();
        lastAdded = 0;
        ultrasequencerOrder.clear();
        knownItems.clear();
        if (!ConfigManager.autoEnchantTableGame.getValue() || !(screen instanceof ContainerScreen containerScreen)) return;
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
            if(currentExperiment != ExperimentType.NONE) ScreenEvents.afterExtract(screen).register(this::onGuiDraw);
        }
    }

    private long pickupItemTestTime = System.currentTimeMillis();

    public void onGuiDraw(Screen screen, GuiGraphicsExtractor drawContext, int mouseX, int mouseY, float tickDelta) {
        // log.info("BackgroundDrawnEvent");
        // log.info("=============[C]==============");
        // log.info(!工具列表.getInstance().事件触发器.skyblock());
        // log.info(!(event.gui instanceof GuiChest));
        // log.info(Config管理.getSBAutoPlayEnchant() != 1);
        // log.info("=============[C]==============");
        if (!(screen instanceof ContainerScreen)) return;
        ChestMenu menu = ((ContainerScreen) screen).getMenu();
        Container container = menu.getContainer();
        if (container instanceof SimpleContainer simpleContainer) {

            if(false) {
                if(System.currentTimeMillis() - pickupItemTestTime > 1000) {
                    pickupItemTestTime = System.currentTimeMillis();
                    Optional<Slot> slot = menu.slots.stream().filter(i -> !i.getItem().isEmpty()).findAny();
                    slot.ifPresent(slot1 -> mc.gameMode.handleContainerInput(menu.containerId, slot1.index, 0, ContainerInput.PICKUP, mc.player));
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
                        mc.gameMode.handleContainerInput(menu.containerId, chronomatronOrder.get(clicks).getKey(), 0, ContainerInput.PICKUP, mc.player);
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
                            mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, mc.player);
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
    public void registerListeners() {
        ScreenEvents.AFTER_INIT.register(this::onGuiOpen);
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
        CustomFabricEvents.CLIENT_PACKET_EVENT.register(this::onPacket);
        CustomFabricEvents.ON_RENDER_ITEM.register(this::onRenderItem);
    }

    private void onRenderItem(GuiGraphicsExtractor guiGraphicsExtractor, ItemStack itemStack, int x, int y) {

        int count = 0;
        for (ItemStackPack value : knownButHiddenItems.values()) {
            if(value.equals(new ItemStackPack(itemStack))) count++;
        }
        if(count > 0) guiGraphicsExtractor.fill(x, y, x + 16, y + 16, count > 1 ? 0xFF00FF00 : 0xFFFF0000);

    }

    private boolean onPacket(Packet<?> packet, PacketListener packetListener, PacketProcessor packetProcessor) {
        if(ConfigManager.enchantTableSuperPairHelper.getValue()) {
            if(packet instanceof ClientboundContainerSetSlotPacket packet1) {
                if (currentExperiment == ExperimentType.SUPERPAIRS) {
                    if (mc.gui.screen() instanceof ContainerScreen containerScreen) {
                        ChestMenu menu = containerScreen.getMenu();
                        if (packet1.getContainerId() == menu.containerId) {
                            if(packet1.getItem().is(Items.STAINED_GLASS.cyan())) {
                                if(knownItems.containsKey(packet1.getSlot())) {
                                    menu.setItem(packet1.getSlot(), packet1.getStateId(), knownItems.get(packet1.getSlot()).itemStack);
                                    knownButHiddenItems.put(packet1.getSlot(), knownItems.get(packet1.getSlot()));
                                    return true;
                                }
                            } else {
                                knownItems.put(packet1.getSlot(), new ItemStackPack(packet1.getItem()));
                                knownButHiddenItems.remove(packet1.getSlot());
                            }
                        }
                    }
                }
            } else if(packet instanceof ClientboundContainerSetContentPacket(
                    int containerId, int stateId, List<ItemStack> items1, ItemStack carriedItem
            )) {
                if (currentExperiment == ExperimentType.SUPERPAIRS) {
                    if (mc.gui.screen() instanceof ContainerScreen containerScreen) {
                        ChestMenu menu = containerScreen.getMenu();
                        if (containerId == menu.containerId) {
                            List<ItemStack> items = new ArrayList<>(items1);
                            for (int i = 0; i < items.size(); i++) {
                                if(items.get(i).is(Items.STAINED_GLASS.cyan())) {
                                    if(knownItems.containsKey(i)) {
                                        items.set(i, knownItems.get(i).itemStack);
                                        knownButHiddenItems.put(i, knownItems.get(i));
                                    }
                                } else {
                                    knownButHiddenItems.remove(i);
                                }
                            }
                            menu.initializeContents(stateId, items, carriedItem);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
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

    enum ExperimentType {
        CHRONOMATRON,
        ULTRASEQUENCER,
        SUPERPAIRS,
        NONE
    }
}