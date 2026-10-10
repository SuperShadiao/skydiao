package pers.XiaoShadiao.skydiao.screen;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.commons.lang3.function.ToBooleanBiFunction;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.F7AutoTerminal;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;

public class F7TerminalScreen extends ContainerScreen implements IContainerScreenHelper {

    public final F7AutoTerminal.TerminalType terminalType;

    private final int buttonSize;
    private final int buttonSpacing;

    private final Slot[] terminalButtons;

    private final List<Slot> clickedSlots = new ArrayList<>();

    private boolean debugMode;
    private Item minTargetColor;

    private final Object2IntMap<Slot> changeColorRemainClicks = new Object2IntLinkedOpenHashMap<>();
    private boolean cancelledUpdate;

    private ToBooleanBiFunction<Slot, MouseButtonEvent> onSlotClick;

    private final Map<Slot, ItemStack> oldItem = new HashMap<>();

    private final boolean autoMode;

    private int anti100CPSCounter;

    private int currentMelodyLine;

    public F7TerminalScreen(ChestMenu menu, Inventory inventory, Component title, F7AutoTerminal.TerminalType terminalType, boolean autoMode) {
        super(menu, inventory, title);
        this.terminalType = terminalType;
        this.buttonSize = ConfigManager.dungeonf7termuibuttonsize.getValue();
        this.buttonSpacing = 2;

        this.terminalButtons = new Slot[4 * 7];

        this.minTargetColor = CHANGE_COLOR_LIST.getFirst();
        this.autoMode = autoMode;
    }

    public void flagSlotClicked(Slot slot) {
        clickedSlots.add(slot);
    }

    @Override
    protected void containerTick() {
        if(anti100CPSCounter > 2) {
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c100CPS..."));
            onClose();
        }
        if(anti100CPSCounter > 0) anti100CPSCounter--;
    }

    private void updateTerminalButtons() {
        if(cancelledUpdate) return;
        if(
                terminalType == F7AutoTerminal.TerminalType.START_WITH ||
                        terminalType == F7AutoTerminal.TerminalType.CHOOSE_COLOR ||
                        terminalType == F7AutoTerminal.TerminalType.CLICK_ORDER
        ) {
            this.terminalButtons[getCustomIndexByLineCol(1, 1)] = menu.slots.get(getIndexByLineCol(2, 2));
            this.terminalButtons[getCustomIndexByLineCol(1, 2)] = menu.slots.get(getIndexByLineCol(2, 3));
            this.terminalButtons[getCustomIndexByLineCol(1, 3)] = menu.slots.get(getIndexByLineCol(2, 4));
            this.terminalButtons[getCustomIndexByLineCol(1, 4)] = menu.slots.get(getIndexByLineCol(2, 5));
            this.terminalButtons[getCustomIndexByLineCol(1, 5)] = menu.slots.get(getIndexByLineCol(2, 6));
            this.terminalButtons[getCustomIndexByLineCol(1, 6)] = menu.slots.get(getIndexByLineCol(2, 7));
            this.terminalButtons[getCustomIndexByLineCol(1, 7)] = menu.slots.get(getIndexByLineCol(2, 8));

            this.terminalButtons[getCustomIndexByLineCol(2, 1)] = menu.slots.get(getIndexByLineCol(3, 2));
            this.terminalButtons[getCustomIndexByLineCol(2, 2)] = menu.slots.get(getIndexByLineCol(3, 3));
            this.terminalButtons[getCustomIndexByLineCol(2, 3)] = menu.slots.get(getIndexByLineCol(3, 4));
            this.terminalButtons[getCustomIndexByLineCol(2, 4)] = menu.slots.get(getIndexByLineCol(3, 5));
            this.terminalButtons[getCustomIndexByLineCol(2, 5)] = menu.slots.get(getIndexByLineCol(3, 6));
            this.terminalButtons[getCustomIndexByLineCol(2, 6)] = menu.slots.get(getIndexByLineCol(3, 7));
            this.terminalButtons[getCustomIndexByLineCol(2, 7)] = menu.slots.get(getIndexByLineCol(3, 8));

            this.terminalButtons[getCustomIndexByLineCol(3, 1)] = menu.slots.get(getIndexByLineCol(4, 2));
            this.terminalButtons[getCustomIndexByLineCol(3, 2)] = menu.slots.get(getIndexByLineCol(4, 3));
            this.terminalButtons[getCustomIndexByLineCol(3, 3)] = menu.slots.get(getIndexByLineCol(4, 4));
            this.terminalButtons[getCustomIndexByLineCol(3, 4)] = menu.slots.get(getIndexByLineCol(4, 5));
            this.terminalButtons[getCustomIndexByLineCol(3, 5)] = menu.slots.get(getIndexByLineCol(4, 6));
            this.terminalButtons[getCustomIndexByLineCol(3, 6)] = menu.slots.get(getIndexByLineCol(4, 7));
            this.terminalButtons[getCustomIndexByLineCol(3, 7)] = menu.slots.get(getIndexByLineCol(4, 8));

            if(terminalType == F7AutoTerminal.TerminalType.CLICK_ORDER) {
                for(int i = 2; i > 0; i--) {
                    for(int j = 1; j < 8; j++) {
                        this.terminalButtons[getCustomIndexByLineCol(i + 1, j)] = this.terminalButtons[getCustomIndexByLineCol(i, j)];
                    }
                }
                for(int j = 1; j < 8; j++) {
                    this.terminalButtons[getCustomIndexByLineCol(1, j)] = null;
                }
            } else if(terminalType == F7AutoTerminal.TerminalType.CHOOSE_COLOR) {
                this.terminalButtons[getCustomIndexByLineCol(4, 1)] = menu.slots.get(getIndexByLineCol(5, 2));
                this.terminalButtons[getCustomIndexByLineCol(4, 2)] = menu.slots.get(getIndexByLineCol(5, 3));
                this.terminalButtons[getCustomIndexByLineCol(4, 3)] = menu.slots.get(getIndexByLineCol(5, 4));
                this.terminalButtons[getCustomIndexByLineCol(4, 4)] = menu.slots.get(getIndexByLineCol(5, 5));
                this.terminalButtons[getCustomIndexByLineCol(4, 5)] = menu.slots.get(getIndexByLineCol(5, 6));
                this.terminalButtons[getCustomIndexByLineCol(4, 6)] = menu.slots.get(getIndexByLineCol(5, 7));
                this.terminalButtons[getCustomIndexByLineCol(4, 7)] = menu.slots.get(getIndexByLineCol(5, 8));
            }
        }
        if(terminalType == F7AutoTerminal.TerminalType.ON_OFF) {
            this.terminalButtons[getCustomIndexByLineCol(1, 2)] = menu.slots.get(getIndexByLineCol(2, 3));
            this.terminalButtons[getCustomIndexByLineCol(1, 3)] = menu.slots.get(getIndexByLineCol(2, 4));
            this.terminalButtons[getCustomIndexByLineCol(1, 4)] = menu.slots.get(getIndexByLineCol(2, 5));
            this.terminalButtons[getCustomIndexByLineCol(1, 5)] = menu.slots.get(getIndexByLineCol(2, 6));
            this.terminalButtons[getCustomIndexByLineCol(1, 6)] = menu.slots.get(getIndexByLineCol(2, 7));

            this.terminalButtons[getCustomIndexByLineCol(2, 2)] = menu.slots.get(getIndexByLineCol(3, 3));
            this.terminalButtons[getCustomIndexByLineCol(2, 3)] = menu.slots.get(getIndexByLineCol(3, 4));
            this.terminalButtons[getCustomIndexByLineCol(2, 4)] = menu.slots.get(getIndexByLineCol(3, 5));
            this.terminalButtons[getCustomIndexByLineCol(2, 5)] = menu.slots.get(getIndexByLineCol(3, 6));
            this.terminalButtons[getCustomIndexByLineCol(2, 6)] = menu.slots.get(getIndexByLineCol(3, 7));

            this.terminalButtons[getCustomIndexByLineCol(3, 2)] = menu.slots.get(getIndexByLineCol(4, 3));
            this.terminalButtons[getCustomIndexByLineCol(3, 3)] = menu.slots.get(getIndexByLineCol(4, 4));
            this.terminalButtons[getCustomIndexByLineCol(3, 4)] = menu.slots.get(getIndexByLineCol(4, 5));
            this.terminalButtons[getCustomIndexByLineCol(3, 5)] = menu.slots.get(getIndexByLineCol(4, 6));
            this.terminalButtons[getCustomIndexByLineCol(3, 6)] = menu.slots.get(getIndexByLineCol(4, 7));
        }
        if(terminalType == F7AutoTerminal.TerminalType.CHANGE_COLOR) {
            this.terminalButtons[getCustomIndexByLineCol(1, 3)] = menu.slots.get(getIndexByLineCol(2, 4));
            this.terminalButtons[getCustomIndexByLineCol(1, 4)] = menu.slots.get(getIndexByLineCol(2, 5));
            this.terminalButtons[getCustomIndexByLineCol(1, 5)] = menu.slots.get(getIndexByLineCol(2, 6));

            this.terminalButtons[getCustomIndexByLineCol(2, 3)] = menu.slots.get(getIndexByLineCol(3, 4));
            this.terminalButtons[getCustomIndexByLineCol(2, 4)] = menu.slots.get(getIndexByLineCol(3, 5));
            this.terminalButtons[getCustomIndexByLineCol(2, 5)] = menu.slots.get(getIndexByLineCol(3, 6));

            this.terminalButtons[getCustomIndexByLineCol(3, 3)] = menu.slots.get(getIndexByLineCol(4, 4));
            this.terminalButtons[getCustomIndexByLineCol(3, 4)] = menu.slots.get(getIndexByLineCol(4, 5));
            this.terminalButtons[getCustomIndexByLineCol(3, 5)] = menu.slots.get(getIndexByLineCol(4, 6));

            Optional<Item> minColor = CHANGE_COLOR_LIST.stream().min(Comparator.comparingInt(v -> Stream.of(terminalButtons).filter(Objects::nonNull).mapToInt(slot -> Math.abs(calcChangeColorLowestDistance(getItemOrOldItem(slot).getItem(), v))).sum()));
            minColor.ifPresent(item -> {
                this.minTargetColor = item;
                cancelledUpdate = true;
            });
        }
        if(terminalType == F7AutoTerminal.TerminalType.MELODY) {
            int melodyLine = 1;
            int melodyCol = 1;
            for (int i = 2; i <= 5; i++) {
                ItemStack itemStack = getItemOrOldItem(menu.slots.get(getIndexByLineCol(i, 8)));
                if(itemStack.getItem() == Items.LIME_TERRACOTTA) {
                    melodyLine = i;
                    currentMelodyLine = i - 2;
                    break;
                }
            }
            for (int i = 2; i <= 6; i++) {
                ItemStack itemStack = getItemOrOldItem(menu.slots.get(getIndexByLineCol(1, i)));
                if(itemStack.getItem() == Items.MAGENTA_STAINED_GLASS_PANE) {
                    melodyCol = i;
                    break;
                }
            }

//            if(getItemOrOldItem(menu.slots.get(getIndexByLineCol(melodyLine, melodyCol))).getItem() == Items.LIME_STAINED_GLASS_PANE) {
//
//            }
            for (int i = 2; i <= 6; i++) {
//                ItemStack itemStack = getItemOrOldItem(menu.slots.get(getIndexByLineCol(melodyLine, i)));
//                if(itemStack.getItem() == Items.LIME_STAINED_GLASS_PANE) {
//                    melodyCol = i;
//                    break;
//                }
                this.terminalButtons[getCustomIndexByLineCol(2, i - 1)] = menu.slots.get(getIndexByLineCol(melodyLine, i));
                this.terminalButtons[getCustomIndexByLineCol(1, i - 1)] = melodyCol == i ? menu.slots.get(getIndexByLineCol(melodyLine, melodyCol)) : null;
            }
            this.terminalButtons[getCustomIndexByLineCol(2, 7)] = menu.slots.get(getIndexByLineCol(melodyLine, 8));
        }

    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    public void extractContents(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if(debugMode) {
            super.extractContents(graphics, mouseX, mouseY, a);
            return;
        }
        menu.setCarried(ItemStack.EMPTY);
        updateTerminalButtons();

        if(autoMode) {
            drawSubTitle(graphics, "§7小沙雕正在思考, 你无权启用自我意识...");
        }

        if(terminalType == F7AutoTerminal.TerminalType.CLICK_ORDER) {
            drawTitle(graphics, "顺序...");
            List<Slot> slots = Stream.of(terminalButtons)
                    .filter(Objects::nonNull)
                    .filter(slot -> !clickedSlots.contains(slot))
                    .filter(slot -> slot.getItem().getItem() == Items.RED_STAINED_GLASS_PANE)
                    .sorted(Comparator.comparingInt(slot -> slot.getItem().getCount()))
                    .limit(3)
                    .toList();
            int i = 0;
            List<Slot> originSlots = Arrays.asList(terminalButtons);
            for (Slot slot : slots) {
                i++;
                int[] customSlotLineCol = getCustomSlotIndexByIndex(originSlots.indexOf(slot));
                if(i == 1) {
                    drawCustomSlot(graphics, customSlotLineCol, 0, 255, 0, 255);
                    onSlotClick = (slot2, _) -> slot2 != slot;
                } else if(i == 2) {
                    drawCustomSlot(graphics, customSlotLineCol, 255, 255, 0, 175);
                } else if(i == 3) {
                    drawCustomSlot(graphics, customSlotLineCol, 255, 0, 0, 120);
                }
                drawTextAtCustomSlot(graphics, String.valueOf(getItemOrOldItem(slot).getCount()), customSlotLineCol);
            }

            int customSlotIndex = -1;
            for (Slot button : terminalButtons) {
                customSlotIndex++;
                if(button != null && !slots.contains(button)) {
                    ItemStack itemStack = getItemOrOldItem(button);
                    if(itemStack.getItem() == Items.RED_STAINED_GLASS_PANE || itemStack.getItem() == Items.LIME_STAINED_GLASS_PANE) {
                        int[] customSlotLineCol = getCustomSlotIndexByIndex(customSlotIndex);
                        drawCustomSlot(graphics, customSlotLineCol, 15, 15, 15, 255);
                        drawTextAtCustomSlot(graphics, String.valueOf(itemStack.getCount()), customSlotLineCol);
                    }
                }
            }

            return;
        }

        int customSlotIndex = -1;
        for (Slot slot : terminalButtons) {
            customSlotIndex++;
            int[] customSlotLineCol = getCustomSlotIndexByIndex(customSlotIndex);
            if(slot != null) {
                ItemStack item = getItemOrOldItem(slot);
                switch (terminalType) {
                    case START_WITH -> {
                        drawTitle(graphics, "选择...");
                        if (ToolList.getInstance().stringHasContext(terminalType.arg)) {
                            String s = ToolList.getInstance().deleteColorCode(item.getHoverName().getString());
                            if (s.toLowerCase().startsWith(terminalType.arg.toLowerCase()) && !clickedSlots.contains(slot)
                            ) {
                                drawCustomSlot(graphics, customSlotLineCol, 0, 255, 0, 255);
                            } else {
                                terminalButtons[customSlotIndex] = null;
                            }
                        }
                    }
                    case CHOOSE_COLOR -> {
                        drawTitle(graphics, "色盲...");
                        if (ToolList.getInstance().stringHasContext(terminalType.arg)) {
                            String colorRaw = terminalType.arg;
                            colorRaw = colorRaw.replace("SILVER", "LIGHT GRAY");
                            String colorUpperCase = colorRaw.toUpperCase();
                            String colorItemID = colorUpperCase.replace(" ", "_");
                            Optional<DyeColor> dye = Arrays.stream(DyeColor.values()).filter(v -> v.name().equalsIgnoreCase(colorItemID)).findFirst();
                            if (dye.isPresent()) {
                                String s = ToolList.getInstance().deleteColorCode(item.getHoverName().getString());
                                String itemUpCase = s.toUpperCase();

                                if (!clickedSlots.contains(slot)
                                        && (itemUpCase.startsWith(colorUpperCase) || itemUpCase.endsWith(colorUpperCase) || (item.getItem().getDescriptionId().contains(colorItemID.toLowerCase()) && !item.getItem().getDescriptionId().contains("_" + colorItemID.toLowerCase()))
                                        || matchesSpecialCase(dye.get(), item))) {
                                    drawCustomSlot(graphics, customSlotLineCol, 0, 255, 0, 255);
                                } else {
                                    terminalButtons[customSlotIndex] = null;
                                }
                            }
                        }
                    }
                    case ON_OFF -> {
                        drawTitle(graphics, "开关...");
                        if (item.getItem() == Items.RED_STAINED_GLASS_PANE && !clickedSlots.contains(slot)) {
                            drawCustomSlot(graphics, customSlotLineCol, 255, 20, 0, 255);
                        } else {
                            terminalButtons[customSlotIndex] = null;
                        }
                    }
                    case CHANGE_COLOR -> {
                        drawTitle(graphics, "同色...");
                        if(autoMode) {
                            Color color = COLOR_MAP.get(item.getItem());
                            if(color != null) {
                                drawCustomSlot(graphics, customSlotLineCol, color.getRed(), color.getGreen(), color.getBlue(), 255);
                            }
                        } else {
                            drawFooterTitle(graphics, "§7左键点击正数按钮, 右键点击负数按钮");
                            int i = calcChangeColorLowestDistance(item.getItem(), minTargetColor);
                            if (changeColorRemainClicks.containsKey(slot)) {
                                i = changeColorRemainClicks.getInt(slot);
                            } else if (i != 0) {
                                changeColorRemainClicks.put(slot, i);
                            }
                            onSlotClick = (slot2, button) -> {
                                int clicks = changeColorRemainClicks.getInt(slot2);
                                if (button.button() == 0 && clicks > 0) {
                                    changeColorRemainClicks.put(slot2, clicks - 1);
                                    return false;
                                } else if (button.button() == 1 && clicks < 0) {
                                    changeColorRemainClicks.put(slot2, clicks + 1);
                                    return false;
                                }
                                return true;
                            };
                            if (i != 0) {
                                int j = Math.abs(i);
                                drawCustomSlot(graphics, customSlotLineCol, i < 0 ? 0 : 255, 125, i > 0 ? 0 : 255, 255 / (3 - j));
                                drawTextAtCustomSlot(graphics, String.valueOf(i), customSlotLineCol);
                            } else if (!item.isEmpty()) {
                                terminalButtons[customSlotIndex] = null;
                            }
                        }
                    }
                    case MELODY -> {
                        drawTitle(graphics, "鸟拉条...");
                        drawFooterTitle(graphics, (currentMelodyLine) + "/3");
                        if (customSlotLineCol[0] == 0) {
                            int x = getButtonX(customSlotLineCol[1]);
                            int y = getButtonY(customSlotLineCol[0]);
                            RenderUtils.drawOutlineRect(graphics, x, y, x + buttonSize, y + buttonSize * 3 + buttonSpacing * 2, 0xFFFF00AA);
                        } else {
                            if(item.getItem() == Items.LIME_STAINED_GLASS_PANE || item.getItem() == Items.LIME_TERRACOTTA) {
                                drawCustomSlot(graphics, customSlotLineCol, 0, 255, 0, 255);
                            } else if(item.getItem() == Items.RED_STAINED_GLASS_PANE) {
                                drawCustomSlot(graphics, customSlotLineCol, 30, 30, 30, 255);
                            }
                        }
                    }
                    default -> drawCustomSlot(graphics, customSlotLineCol, 10, 10, 10, 255);
                }

                if(isButtonHovered(customSlotLineCol, mouseX, mouseY)) {
                    drawCustomSlot(graphics, customSlotLineCol, 255, 255, 255, 50);
                }
            }
        }
    }

    private boolean isButtonHovered(int[] customSlotLineCol, int mouseX, int mouseY) {
        int x = getButtonX(customSlotLineCol[1]);
        int y = getButtonY(customSlotLineCol[0]);
        return mouseX >= x && mouseX <= x + buttonSize && mouseY >= y && mouseY <= y + buttonSize;
    }

    private void drawCustomSlot(GuiGraphicsExtractor graphics, int[] lineCol, int red, int green, int blue, int alpha) {
        drawCustomSlot(graphics, lineCol[0], lineCol[1], red, green, blue, alpha);
    }

    private int getButtonX(int col) {
        return switch (col) {
            case 0 -> width / 2 - (int) (buttonSize * 3.5 + buttonSpacing * 3);
            case 1 -> width / 2 - (int) (buttonSize * 2.5 + buttonSpacing * 2);
            case 2 -> width / 2 - (int) (buttonSize * 1.5 + buttonSpacing);
            case 3 -> width / 2 - (int) (buttonSize * 0.5);
            case 4 -> width / 2 + (int) (buttonSize * 0.5 + buttonSpacing);
            case 5 -> width / 2 + (int) (buttonSize * 1.5 + buttonSpacing * 2);
            default -> width / 2 + (int) (buttonSize * 2.5 + buttonSpacing * 3);
        };
    }

    private int getButtonY(int line) {
        return switch (line) {
            case 0 -> height / 2 - (buttonSize + buttonSpacing);
            case 1 -> height / 2;
            case 2 -> height / 2 + buttonSize + buttonSpacing;
            default -> height / 2 + buttonSize * 2 + buttonSpacing * 2;
        } - 6;
    }

    private void drawCustomSlot(GuiGraphicsExtractor graphics, int line, int col, int red, int green, int blue, int alpha) {
        int x = getButtonX(col);
        int y = getButtonY(line);

        graphics.fill(x, y, x + buttonSize, y + buttonSize, alpha << 24 | red << 16 | green << 8 | blue);
    }

    private void drawTextAtCustomSlot(GuiGraphicsExtractor graphics, String text, int[] lineCol) {
        drawTextAtCustomSlot(graphics, text, lineCol[0], lineCol[1]);
    }

    private void drawTextAtCustomSlot(GuiGraphicsExtractor graphics, String text, int line, int col) {
        int x = getButtonX(col);
        int y = getButtonY(line);

        graphics.centeredText(font, text, x + buttonSize / 2, y + (buttonSize - font.lineHeight) / 2, 0xFFFFFFFF);
    }

    private void drawTitle(GuiGraphicsExtractor graphics, String title) {
        graphics.centeredText(font, title, width / 2, getTopY() + 10, 0xFFFFFFFF);
    }

    private void drawSubTitle(GuiGraphicsExtractor graphics, String title) {
        graphics.centeredText(font, title, width / 2, getTopY() + 16 + font.lineHeight, 0xFFFFFFFF);
    }

    private void drawFooterTitle(GuiGraphicsExtractor graphics, String title) {
        graphics.centeredText(font, title, width / 2, getBottumY() - 10 - font.lineHeight, 0xFFFFFFFF);
    }

    private int[] getCustomSlotIndexByIndex(int index) {
        return new int[] {index / 7, index % 7};
    }

    private int getCustomIndexByLineColIndex(int line, int col) {
        return line * 7 + col;
    }

    private int getCustomIndexByLineCol(int line, int col) {
        return (line - 1) * 7 + (col - 1);
    }

    @Override
    public void extractCarriedItem(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if(debugMode) {
            super.extractCarriedItem(graphics, mouseX, mouseY);
        }
    }

    @Override
    public void extractBackground(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if(debugMode) {
            super.extractBackground(graphics, mouseX, mouseY, a);
            return;
        }
        int background = ConfigManager.dungeonf7termuibackground.getValue();
        if(background == 1) {
            extractBlurredBackground(graphics);
        } else if(background == 0) {
            extractMenuBackground(graphics);
        }

        graphics.fill(width / 2 - (4 * buttonSize + buttonSpacing * 2), getTopY(), width / 2 + (4 * buttonSize + buttonSpacing * 2), getBottumY(), 0xFF000000);
    }

    private int getBottumY() {
        return height / 2 + (int) (2.5 * buttonSize + buttonSpacing * 2) + 10;
    }

    private int getTopY() {
        return height / 2 - (int) (2.5 * buttonSize + buttonSpacing * 2) - 10;
    }

    @Override
    protected void extractSlot(@NotNull GuiGraphicsExtractor graphics, @NotNull Slot slot, int mouseX, int mouseY) {
        if(debugMode) {
            super.extractSlot(graphics, slot, mouseX, mouseY);
        }
    }

    @Override
    protected void extractLabels(@NotNull GuiGraphicsExtractor graphics, int xm, int ym) {
        if(debugMode) {
            super.extractLabels(graphics, xm, ym);
        }
    }

    @Override
    protected void extractTooltip(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if(debugMode) {
            super.extractTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    public void extractSnapbackItem(@NotNull GuiGraphicsExtractor graphics) {
        if(debugMode) {
            super.extractSnapbackItem(graphics);
        }
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent event, boolean doubleClick) {
        if(autoMode) {
            return false;
        }
        if(debugMode) {
            return super.mouseClicked(event, doubleClick);
        }
        int customSlotIndex = -1;
        for (Slot slot : terminalButtons) {
            customSlotIndex++;
            int[] customSlotLineCol = getCustomSlotIndexByIndex(customSlotIndex);
            if (slot != null) {
                if (isButtonHovered(customSlotLineCol, (int) event.x(), (int) event.y())) {
                    if(onSlotClick != null && onSlotClick.applyAsBoolean(slot, event)) {
                        return false;
                    }
                    clickSlot(slot, event.button());
                    if(!cancelledUpdate) terminalButtons[customSlotIndex] = null;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent event) {
        return false;
    }

    public void clickSlot(Slot slot, int mouseButton) {
        anti100CPSCounter++;
        flagSlotClicked(slot);

        slotClicked(slot, slot.index, mouseButton, ContainerInput.PICKUP);
    }

    protected void slotClicked(final @NotNull Slot slot, int slotId, final int buttonNum, final ContainerInput containerInput) {
        this.minecraft.gameMode.handleContainerInput(this.menu.containerId, slotId, buttonNum, containerInput, this.minecraft.player);
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

    @Override
    public boolean keyPressed(@NotNull KeyEvent event) {
        if(ToolList.getInstance().isDevEnvironment() && event.hasShiftDown()) {
            debugMode = !debugMode;
        }
        return super.keyPressed(event);
    }

    private ItemStack getItemOrOldItem(Slot slot) {
        ItemStack item = slot.getItem();
        ItemStack retItem = oldItem.get(slot);
        if(!item.isEmpty() || retItem == null) {
            oldItem.put(slot, item.copy());
            return item;
        }
        return retItem;
    }

    private final List<Item> CHANGE_COLOR_LIST = List.of(
            Items.ORANGE_STAINED_GLASS_PANE,
            Items.YELLOW_STAINED_GLASS_PANE,
            Items.GREEN_STAINED_GLASS_PANE,
            Items.BLUE_STAINED_GLASS_PANE,
            Items.RED_STAINED_GLASS_PANE
    );

    private final Map<Item, Color> COLOR_MAP = Map.of(
            Items.ORANGE_STAINED_GLASS_PANE, Color.ORANGE,
            Items.YELLOW_STAINED_GLASS_PANE, Color.YELLOW,
            Items.GREEN_STAINED_GLASS_PANE, Color.GREEN,
            Items.BLUE_STAINED_GLASS_PANE, Color.BLUE,
            Items.RED_STAINED_GLASS_PANE, Color.RED
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
}
