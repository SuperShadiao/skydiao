package pers.XiaoShadiao.skydiao.eventbuslistener;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class F7AutoTerminal extends AbstractListener implements IDungeonListener {

    private final List<Item> CHANGE_COLOR_LIST = List.of(
            Items.ORANGE_STAINED_GLASS_PANE,
            Items.YELLOW_STAINED_GLASS_PANE,
            Items.GREEN_STAINED_GLASS_PANE,
            Items.BLUE_STAINED_GLASS_PANE,
            Items.RED_STAINED_GLASS_PANE
    );

    private int calcChangeColorLowestDistance(Item from, Item to) {
        // AI生成。
        int fromIndex = CHANGE_COLOR_LIST.indexOf(from);
        int toIndex = CHANGE_COLOR_LIST.indexOf(to);
        
        if (fromIndex == -1 || toIndex == -1) {
            return 0; // Return 0 if either item is not in the list
        }
        
        int size = CHANGE_COLOR_LIST.size();
        int directDistance = toIndex - fromIndex;
        int wrapDistance;
        
        if (directDistance > 0) {
            wrapDistance = directDistance - size;
        } else {
            wrapDistance = directDistance + size;
        }
        
        // Calculate absolute values to determine which is shorter
        if (Math.abs(directDistance) <= Math.abs(wrapDistance)) {
            return directDistance;
        } else {
            return wrapDistance;
        }
    }

    private TerminalType currentTerminal;

    private long openScreenTime;

    private record Click(int containerId, Slot slot, boolean rightClick) {
        public Click(int containerId, Slot slot) {
            this(containerId, slot, false);
        }
    }

    private Click pendingClick;
    private Click lastClick;
    private long lastClickTime;
    private final IntSet blacklistedSlots = new IntOpenHashSet();

    @Override
    public String getListenerName() {
        return "F7AutoTerminal";
    }

    @Override
    public void registerListeners() {
        ScreenEvents.AFTER_INIT.register(this::afterScreenInit);
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
        ClientReceiveMessageEvents.GAME.register(this::onChat);
    }

    private void onChat(Component component, boolean b) {
        String message = ToolList.getInstance().deleteColorCode(component.getString());
        if("Already selected!".equals(message)) {
            blacklistedSlots.add(lastClick.slot.index);
        }
    }

    private int currentMelodyLine;

    private void onClientTick(Minecraft mc) {
        if(mc.player == null || mc.level == null || !(mc.screen instanceof ContainerScreen containerScreen)) {
            if(currentTerminal == TerminalType.MELODY && mc.screen == null) {
                if(currentMelodyLine == 2) {
                    sendDungeonF7ChatMessage(ConfigManager.dungeonf7msgbotmelody[3].getValue());
                }
            }
            blacklistedSlots.clear();
            currentTerminal = null;
            return;
        }
        if(System.currentTimeMillis() - openScreenTime < 350) {
            return;
        }
        ChestMenu menu = containerScreen.getMenu();
        ItemStack carriedItem = menu.getCarried();      // 牢地服务器喜欢Lag, 所以判断这个东西避免频繁发包

        if(currentTerminal != null) {
            Slot[][] slots = ToolList.getInstance().mapSlotsToArray(menu);
            List<Slot> slots1 = menu.slots.stream().filter(slot -> slot.container != mc.player.getInventory()).toList();

            if (carriedItem.isEmpty()) {
                switch (currentTerminal) {
                    case MELODY -> {
                        int col = -1;
                        for (int i = 0; i < slots.length; i++) {
                            Slot slot = slots[i][0];
                            if (slot.getItem().getItem() == Items.MAGENTA_STAINED_GLASS_PANE) {
                                col = i;
                                break;
                            }
                        }
                        if (col != -1) {
                            for (int i = 0; i < slots[col].length; i++) {
                                Slot slot = slots[col][i];
                                if (slot.getItem().getItem() == Items.LIME_STAINED_GLASS_PANE) {
                                    if (slots[7][i].getItem().getItem() == Items.LIME_TERRACOTTA) {
                                        pendingClick = new Click(menu.containerId, slots[7][i]);
                                        break;
                                    }
                                }
                                if (slot.getItem().getItem() == Items.LIME_STAINED_GLASS_PANE || slot.getItem().getItem() == Items.RED_STAINED_GLASS_PANE) {
                                    int tempLine = Mth.clamp(i - 2, -1, 2);
                                    if (currentMelodyLine != tempLine) {
                                        currentMelodyLine = tempLine;
                                        int clamp = Mth.clamp(currentMelodyLine, -1, 2);
                                        if(clamp >= 0) sendDungeonF7ChatMessage(ConfigManager.dungeonf7msgbotmelody[clamp].getValue());
                                    }
                                }
                            }
                        }
                    }
                    case ON_OFF -> {
                        for (Slot slot : slots1) {
                            if (slot.getItem().getItem() == Items.RED_STAINED_GLASS_PANE) {
                                pendingClick = new Click(menu.containerId, slot);
                                break;
                            }
                        }
                    }
                    case START_WITH -> {
                        if (ToolList.getInstance().stringHasContext(currentTerminal.arg)) {
                            for (Slot slot : slots1) {
                                ItemStack item = slot.getItem();
                                if (item.getHoverName() != null) {
                                    String s = ToolList.getInstance().deleteColorCode(item.getHoverName().getString());
                                    System.out.println(s);
                                    if (s.toLowerCase().startsWith(currentTerminal.arg.toLowerCase())
                                            && !ToolList.hasGlint(item)
                                            && !blacklistedSlots.contains(slot.index)
                                    ) {
                                        pendingClick = new Click(menu.containerId, slot);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    case CLICK_ORDER -> {
                        slots1.stream().filter(slot -> slot.getItem().getItem() == Items.RED_STAINED_GLASS_PANE).min(Comparator.comparingInt(slot -> slot.getItem().getCount())).ifPresent(slot -> pendingClick = new Click(menu.containerId, slot));
                    }
                    case CHANGE_COLOR -> {
                        List<Slot> changeColorSlots = new ArrayList<>();
                        for (Slot slot : slots1) {
                            if (CHANGE_COLOR_LIST.contains(slot.getItem().getItem())) {
                                changeColorSlots.add(slot);
                            }
                        }
                        Optional<Item> minColor = CHANGE_COLOR_LIST.stream().min(Comparator.comparingInt(v -> changeColorSlots.stream().mapToInt(slot -> Math.abs(calcChangeColorLowestDistance(slot.getItem().getItem(), v))).sum()));
                        if (minColor.isPresent()) {
                            for (Slot slot : slots1) {
                                int count = calcChangeColorLowestDistance(slot.getItem().getItem(), minColor.get());
                                if (count != 0) {
                                    pendingClick = new Click(menu.containerId, slot, count < 0);
                                    break;
                                }
                            }
                        }
                    }
                    case CHOOSE_COLOR -> {
                        if (ToolList.getInstance().stringHasContext(currentTerminal.arg)) {
                            String colorRaw = currentTerminal.arg;
                            colorRaw = colorRaw.replace("SILVER", "LIGHT GRAY");
                            String colorUpperCase = colorRaw.replace("_", " ").toUpperCase();
                            Optional<DyeColor> dye = Arrays.stream(DyeColor.values()).filter(v -> v.name().equalsIgnoreCase(colorUpperCase)).findFirst();
                            if (dye.isPresent()) {
                                for (Slot slot : slots1) {
                                    ItemStack item = slot.getItem();
                                    String s = ToolList.getInstance().deleteColorCode(item.getHoverName().getString());
                                    String itemUpCase = s.toUpperCase();

                                    if (!ToolList.hasGlint(item)
                                            && !blacklistedSlots.contains(slot.index)
                                            && item.getItem() != Items.BLACK_STAINED_GLASS_PANE
                                            && (itemUpCase.startsWith(colorUpperCase) || itemUpCase.endsWith(colorUpperCase)
                                            || matchesSpecialCase(dye.get(), item))) {
                                        pendingClick = new Click(menu.containerId, slot);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean matchesSpecialCase(DyeColor color, ItemStack item) {
        return switch (color) {
            case DyeColor.BLACK -> item.getItem() == Items.INK_SAC;
            case DyeColor.BLUE -> item.getItem() == Items.LAPIS_LAZULI;
            case DyeColor.BROWN -> item.getItem() == Items.COCOA_BEANS;
            case DyeColor.WHITE -> item.getItem() == Items.BONE_MEAL || item.getItem() == Items.WHITE_WOOL;
            case DyeColor.GREEN -> item.getItem() == Items.CACTUS;
            case DyeColor.RED -> item.getItem() == Items.POPPY;
            case DyeColor.YELLOW -> item.getItem() == Items.DANDELION;
            default -> false;
        };
    }

    private void afterScreenInit(Minecraft mc, Screen screen, int scaledWidth, int scaledHeight) {
        a:{
            for (TerminalType value : TerminalType.values()) {
                if (value.testScreen(screen)) {
                    if(currentTerminal != value) {
                        openScreenTime = System.currentTimeMillis();
                        if(value == TerminalType.MELODY) {
                            openScreenTime += 150;
                            currentMelodyLine = -1;
                            sendDungeonF7ChatMessage(ConfigManager.dungeonf7msgbotmelodystart.getValue());
                        }
                    }
                    currentTerminal = value;
                    pendingClick = null;
                    ScreenEvents.afterRender(screen).register(this::afterScreenRender);
                    break a;
                }
            }
            currentTerminal = null;
        }
    }

    private void afterScreenRender(Screen screen, GuiGraphics guiGraphics, int width, int height, float deltaTick) {
        if (!ConfigManager.dungeonf7autoterm.getValue()) return;
        if (System.currentTimeMillis() - lastClickTime > ToolList.getInstance().random.nextInt(31) + 270 && pendingClick != null) {
            Click temp = lastClick = pendingClick;
            pendingClick = null;
            lastClickTime = System.currentTimeMillis();

            mc.gameMode.handleInventoryMouseClick(temp.containerId, temp.slot.index, temp.rightClick ? 1 : 0, ClickType.PICKUP, mc.player);
        }
    }

    @Override
    public int getFloor() {
        return 7;
    }

    public enum TerminalType {
        ON_OFF("^Correct all the panes!$"),
        CHOOSE_COLOR("^Select all the ([\\w ]+) items!$"),
        START_WITH("^What starts with: '(\\w)'\\?$"),
        MELODY("^Click the button on time!$"),
        CHANGE_COLOR("^Change all to same color!$"),
        CLICK_ORDER("^Click in order!$"),
        ;

        public String arg;

        private final Pattern regex;

        TerminalType(String regex) {
            this.regex = Pattern.compile(regex);
        }

        public boolean testScreen(Screen screen) {
            if(!(screen instanceof ContainerScreen containerScreen)) return false;
            Matcher matcher = regex.matcher(containerScreen.getTitle().getString());
            if(matcher.find()) {
                if(matcher.groupCount() == 1) arg = matcher.group(1);
                return true;
            } else {
                return false;
            }
        }
    }

}