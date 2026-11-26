package pers.XiaoShadiao.skydiao.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import pers.XiaoShadiao.skydiao.config.QuickNavConfig;
import pers.XiaoShadiao.skydiao.eventbuslistener.quicknav.QuickNavListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * QuickNav 按钮编辑界面。
 */
public class QuickNavConfigScreen extends Screen {

    private static final int MAX_BUTTONS = 14;
    private static final int ROW_HEIGHT = 24;

    private final Screen parent;
    private final List<QuickNavConfig.Entry> entries;
    private final int[] labelYs = new int[5];

    private ButtonList buttonList;
    private EditBox iconBox;
    private EditBox componentsBox;
    private EditBox commandBox;
    private EditBox tooltipBox;
    private EditBox regexBox;

    private Button iconPickerButton;
    private Button doubleClickButton;
    private Button addButton;
    private Button deleteButton;
    private Button upButton;
    private Button downButton;

    private ItemStack previewStack = ItemStack.EMPTY;
    private boolean updatingFields;
    private int selectedIndex;

    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;
    private int formX;
    private int formY;
    private int formWidth;

    public QuickNavConfigScreen(Screen parent) {
        super(Component.literal("QuickNav 配置"));
        this.parent = parent;
        this.entries = new ArrayList<>(QuickNavConfig.load().buttons);
    }

    @Override
    protected void init() {
        int margin = 10;
        int top = 30;
        int bottom = height - 38;
        int contentWidth = Math.min(560, width - margin * 2);
        int left = (width - contentWidth) / 2;
        int leftWidth = Math.max(160, contentWidth * 2 / 5 - 4);
        int gap = 8;
        int rightWidth = contentWidth - leftWidth - gap;

        listX = left;
        listY = top + 14;
        listWidth = leftWidth;
        listHeight = Math.max(60, bottom - listY);
        formX = left + leftWidth + gap;
        formY = top;
        formWidth = rightWidth;

        buttonList = new ButtonList(minecraft, listWidth, listHeight, listY, ROW_HEIGHT);
        buttonList.setX(listX);
        addRenderableWidget(buttonList);

        int labelWidth = Math.min(56, formWidth / 3);
        int boxX = formX + labelWidth;
        int boxWidth = Math.max(60, formWidth - labelWidth);
        int rowY = formY + 4;
        for (int i = 0; i < 5; i++) {
            labelYs[i] = rowY;
            rowY += 23;
        }

        iconBox = addField(boxX, labelYs[0], boxWidth, 512);
        componentsBox = addField(boxX, labelYs[1], boxWidth, 4096);
        commandBox = addField(boxX, labelYs[2], boxWidth, 256);
        tooltipBox = addField(boxX, labelYs[3], boxWidth, 256);
        regexBox = addField(boxX, labelYs[4], boxWidth, 512);

        iconBox.setResponder(_ -> onFieldChanged());
        componentsBox.setResponder(_ -> onFieldChanged());
        commandBox.setResponder(_ -> onFieldChanged());
        tooltipBox.setResponder(_ -> onFieldChanged());
        regexBox.setResponder(_ -> onFieldChanged());

        int halfWidth = (formWidth - 6) / 2;
        int actionY = rowY + 2;
        iconPickerButton = addRenderableWidget(Button.builder(Component.literal("选择物品"), _ -> openIconPicker())
                .bounds(formX, actionY, halfWidth, 20).build());
        doubleClickButton = addRenderableWidget(Button.builder(Component.literal("双击触发: 关"), _ -> toggleDoubleClick())
                .bounds(formX + halfWidth + 6, actionY, halfWidth, 20).build());

        actionY += 24;
        int quarterWidth = (formWidth - 18) / 4;
        addButton = addRenderableWidget(Button.builder(Component.literal("新增"), _ -> addEntry())
                .bounds(formX, actionY, quarterWidth, 20).build());
        deleteButton = addRenderableWidget(Button.builder(Component.literal("删除"), _ -> deleteEntry())
                .bounds(formX + quarterWidth + 6, actionY, quarterWidth, 20).build());
        upButton = addRenderableWidget(Button.builder(Component.literal("上移"), _ -> moveEntry(-1))
                .bounds(formX + (quarterWidth + 6) * 2, actionY, quarterWidth, 20).build());
        downButton = addRenderableWidget(Button.builder(Component.literal("下移"), _ -> moveEntry(1))
                .bounds(formX + (quarterWidth + 6) * 3, actionY, quarterWidth, 20).build());

        int footerY = height - 28;
        int footerX = width / 2 - 135;
        addRenderableWidget(Button.builder(Component.literal("§a保存"), _ -> save())
                .bounds(footerX, footerY, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), _ -> onClose())
                .bounds(footerX + 90, footerY, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("恢复默认"), _ -> restoreDefaults())
                .bounds(footerX + 180, footerY, 90, 20).build());

        rebuildList(Math.min(selectedIndex, Math.max(0, entries.size() - 1)));
    }

    private EditBox addField(int x, int y, int width, int maxLength) {
        EditBox box = new EditBox(font, x, y, width, 18, Component.empty());
        box.setMaxLength(maxLength);
        addRenderableWidget(box);
        return box;
    }

    private QuickNavConfig.Entry selectedEntry() {
        return selectedIndex >= 0 && selectedIndex < entries.size() ? entries.get(selectedIndex) : null;
    }

    private void rebuildList(int index) {
        if (buttonList == null) {
            return;
        }
        buttonList.rebuild();
        if (entries.isEmpty()) {
            selectedIndex = -1;
            buttonList.setSelected(null);
            populateFields();
            updateButtonStates();
            return;
        }
        selectedIndex = Math.max(0, Math.min(index, entries.size() - 1));
        if (selectedIndex < buttonList.children().size()) {
            buttonList.setSelected(buttonList.children().get(selectedIndex));
        } else {
            buttonList.setSelected(null);
            populateFields();
        }
        updateButtonStates();
    }

    private void populateFields() {
        QuickNavConfig.Entry entry = selectedEntry();
        updatingFields = true;
        if (entry == null) {
            iconBox.setValue("");
            componentsBox.setValue("");
            commandBox.setValue("");
            tooltipBox.setValue("");
            regexBox.setValue("");
            previewStack = ItemStack.EMPTY;
            updatingFields = false;
            updateButtonStates();
            return;
        }
        iconBox.setValue(entry.icon == null ? "" : entry.icon);
        componentsBox.setValue(entry.components == null ? "" : entry.components);
        commandBox.setValue(entry.command == null ? "" : entry.command);
        tooltipBox.setValue(entry.tooltip == null ? "" : entry.tooltip);
        regexBox.setValue(entry.titleRegex == null ? "" : entry.titleRegex);
        doubleClickButton.setMessage(Component.literal("双击触发: " + (entry.doubleClick ? "开" : "关")));
        previewStack = QuickNavListener.createIcon(entry);
        updatingFields = false;
        updateButtonStates();
    }

    private void onFieldChanged() {
        if (updatingFields) {
            return;
        }
        QuickNavConfig.Entry entry = selectedEntry();
        if (entry == null) {
            return;
        }
        entry.icon = iconBox.getValue().trim();
        entry.components = componentsBox.getValue();
        entry.command = commandBox.getValue().trim();
        entry.tooltip = tooltipBox.getValue();
        String regex = regexBox.getValue().trim();
        entry.titleRegex = regex.isEmpty() ? null : regex;
        previewStack = QuickNavListener.createIcon(entry);
    }

    private void toggleDoubleClick() {
        QuickNavConfig.Entry entry = selectedEntry();
        if (entry == null) {
            return;
        }
        entry.doubleClick = !entry.doubleClick;
        doubleClickButton.setMessage(Component.literal("双击触发: " + (entry.doubleClick ? "开" : "关")));
    }

    private void addEntry() {
        if (entries.size() >= MAX_BUTTONS) {
            return;
        }
        QuickNavConfig.Entry entry = new QuickNavConfig.Entry("minecraft:barrier", "", "/", "New", null, false);
        entries.add(entry);
        rebuildList(entries.size() - 1);
    }

    private void deleteEntry() {
        if (entries.size() <= 1 || selectedEntry() == null) {
            return;
        }
        entries.remove(selectedIndex);
        rebuildList(selectedIndex);
    }

    private void moveEntry(int delta) {
        int target = selectedIndex + delta;
        if (selectedIndex < 0 || target < 0 || target >= entries.size()) {
            return;
        }
        Collections.swap(entries, selectedIndex, target);
        rebuildList(target);
    }

    private void restoreDefaults() {
        entries.clear();
        entries.addAll(QuickNavConfig.defaults().buttons);
        rebuildList(0);
    }

    private void save() {
        QuickNavConfig.Entry entry = selectedEntry();
        if (entry != null) {
            entry.icon = iconBox.getValue().trim();
            entry.components = componentsBox.getValue();
            entry.command = commandBox.getValue().trim();
            entry.tooltip = tooltipBox.getValue();
            String regex = regexBox.getValue().trim();
            entry.titleRegex = regex.isEmpty() ? null : regex;
        }
        QuickNavConfig config = new QuickNavConfig();
        config.buttons = new ArrayList<>(entries);
        config.save();
        QuickNavListener.reloadEntries();
        onClose();
    }

    private void updateButtonStates() {
        boolean hasEntry = selectedEntry() != null;
        iconBox.active = hasEntry;
        componentsBox.active = hasEntry;
        commandBox.active = hasEntry;
        tooltipBox.active = hasEntry;
        regexBox.active = hasEntry;
        iconPickerButton.active = hasEntry;
        doubleClickButton.active = hasEntry;
        deleteButton.active = hasEntry && entries.size() > 1;
        upButton.active = hasEntry && selectedIndex > 0;
        downButton.active = hasEntry && selectedIndex < entries.size() - 1;
        addButton.active = entries.size() < MAX_BUTTONS;
    }

    private void openIconPicker() {
        QuickNavConfig.Entry entry = selectedEntry();
        if (entry == null) {
            return;
        }
        minecraft.gui.setScreen(new NeuItemSelectScreen(this, "选择 QuickNav 图标", item -> {
            entry.icon = "skyblock:" + item.internalName();
            entry.components = "";
            populateFields();
            rebuildList(selectedIndex);
        }));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        graphics.text(font, "按钮", listX, listY - 12, 0xFFFFFFFF);
        String[] labels = {"图标 ID", "组件", "指令", "Tooltip", "标题正则"};
        for (int i = 0; i < labels.length; i++) {
            graphics.text(font, labels[i], formX, labelYs[i] + 5, 0xFFFFFFFF);
        }
        if (!previewStack.isEmpty()) {
            graphics.text(font, "预览", width - 62, 10, 0xFFFFFFFF);
            graphics.item(minecraft.player, previewStack, width - 38, 18, 0);
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    private class ButtonList extends ObjectSelectionList<ButtonListEntry> {

        private ButtonList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        private void rebuild() {
            clearEntries();
            for (int i = 0; i < entries.size(); i++) {
                addEntry(new ButtonListEntry(i, entries.get(i)));
            }
        }

        @Override
        public void setSelected(ButtonListEntry entry) {
            super.setSelected(entry);
            if (entry != null) {
                selectedIndex = entry.index;
            }
            populateFields();
            updateButtonStates();
        }

        @Override
        public int getRowWidth() {
            return getWidth() - 12;
        }

        @Override
        protected int scrollBarX() {
            return getX() + getWidth() - 6;
        }
    }

    private class ButtonListEntry extends ObjectSelectionList.Entry<ButtonListEntry> {

        private final int index;
        private final QuickNavConfig.Entry entry;
        private final ItemStack icon;

        private ButtonListEntry(int index, QuickNavConfig.Entry entry) {
            this.index = index;
            this.entry = entry;
            this.icon = QuickNavListener.createIcon(entry);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
            int x = getContentX();
            int y = getContentY();
            graphics.item(minecraft.player, icon, x + 2, y + (getContentHeight() - 16) / 2, 0);
            String name = stripColors(entry.tooltip == null ? "" : entry.tooltip);
            String command = entry.command == null ? "" : entry.command;
            String line = name.isEmpty() ? command : name + " §7" + command;
            line = font.plainSubstrByWidth(line, getContentWidth() - 24);
            graphics.text(font, line, x + 22, y + (getContentHeight() - font.lineHeight) / 2, 0xFFFFFFFF);
        }

        @Override
        public Component getNarration() {
            return Component.literal(entry.tooltip == null ? entry.command : entry.tooltip);
        }
    }

    private static final Pattern COLOR_PATTERN = Pattern.compile("§.");

    private static String stripColors(String value) {
        return value == null ? "" : COLOR_PATTERN.matcher(value).replaceAll("");
    }
}
