package pers.XiaoShadiao.skydiao.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.WaypointListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.WaypointListener.Waypoint;
import pers.XiaoShadiao.skydiao.utils.screen.XSDSliderButton;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class WaypointSettingsScreen extends Screen {

    private static final int LIST_Y = 44;
    private static final int LIST_BOTTOM_MARGIN = 90;

    private final Screen parent;
    private final Map<String, List<Waypoint>> configs = new LinkedHashMap<>();

    private ConfigFileList configFileList;
    private WaypointList waypointList;
    private String selectedFileName = WaypointListener.currentEditingFileName;

    private Button renameConfigButton;
    private Button deleteConfigButton;
    private Button addWaypointButton;
    private Button editWaypointButton;
    private Button toggleEnabledButton;
    private Button toggleAllEnabledButton;
    private Button deleteWaypointButton;

    public WaypointSettingsScreen() {
        this(null);
    }

    public WaypointSettingsScreen(Screen parent) {
        super(Component.literal("路径点设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        reloadConfigs();

        int gap = 8;
        int contentWidth = width - 20;
        int leftWidth = contentWidth / 3;
        int rightWidth = contentWidth - leftWidth - gap;
        int listHeight = Math.max(40, height - LIST_Y - LIST_BOTTOM_MARGIN);

        configFileList = new ConfigFileList(minecraft, leftWidth, listHeight, LIST_Y, 24);
        configFileList.setX(10);
        waypointList = new WaypointList(minecraft, rightWidth, listHeight, LIST_Y, 30);
        waypointList.setX(10 + leftWidth + gap);
        addRenderableWidget(configFileList);
        addRenderableWidget(waypointList);

        StringWidget titleWidget = new StringWidget(title, font);
        titleWidget.setPosition((width - font.width(title)) / 2, 10);
        addRenderableWidget(titleWidget);

        Component configHeader = Component.literal("配置文件");
        StringWidget configHeaderWidget = new StringWidget(configHeader, font);
        configHeaderWidget.setPosition(10 + (leftWidth - font.width(configHeader)) / 2, 30);
        addRenderableWidget(configHeaderWidget);

        Component waypointHeader = Component.literal("路径点");
        StringWidget waypointHeaderWidget = new StringWidget(waypointHeader, font);
        waypointHeaderWidget.setPosition(10 + leftWidth + gap + (rightWidth - font.width(waypointHeader)) / 2, 30);
        addRenderableWidget(waypointHeaderWidget);

        int buttonGap = 4;
        int buttonWidth = (contentWidth - buttonGap * 3) / 4;
        int firstRowY = height - 78;
        int secondRowY = height - 54;
        int x = 10;
        addRenderableWidget(Button.builder(Component.literal("新建配置"), _ -> openNewConfigEditor())
                .bounds(x, firstRowY, buttonWidth, 20).build());
        x += buttonWidth + buttonGap;
        renameConfigButton = addRenderableWidget(Button.builder(Component.literal("重命名"), _ -> openRenameConfigEditor())
                .bounds(x, firstRowY, buttonWidth, 20).build());
        x += buttonWidth + buttonGap;
        deleteConfigButton = addRenderableWidget(Button.builder(Component.literal("删除配置"), _ -> confirmDeleteConfig())
                .bounds(x, firstRowY, buttonWidth, 20).build());
        x += buttonWidth + buttonGap;
        toggleAllEnabledButton = addRenderableWidget(Button.builder(Component.literal("禁用/启用所有"), _ -> toggleAllEnabled())
                .bounds(x, firstRowY, buttonWidth, 20).build());

        x = 10;
        addWaypointButton = addRenderableWidget(Button.builder(Component.literal("添加路径点"), _ -> openWaypointEditor(null))
                .bounds(x, secondRowY, buttonWidth, 20).build());
        x += buttonWidth + buttonGap;
        editWaypointButton = addRenderableWidget(Button.builder(Component.literal("编辑路径点"), _ -> editSelectedWaypoint())
                .bounds(x, secondRowY, buttonWidth, 20).build());
        x += buttonWidth + buttonGap;
        toggleEnabledButton = addRenderableWidget(Button.builder(Component.literal("启用/禁用"), _ -> toggleSelectedEnabled())
                .bounds(x, secondRowY, buttonWidth, 20).build());
        x += buttonWidth + buttonGap;
        deleteWaypointButton = addRenderableWidget(Button.builder(Component.literal("删除路径点"), _ -> confirmDeleteWaypoint())
                .bounds(x, secondRowY, buttonWidth, 20).build());

        addRenderableWidget(Button.builder(Component.literal("打开路径点文件夹"), _ -> WaypointListener.openWaypointFolder())
                .bounds(width / 2 - 155, height - 30, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("刷新"), _ -> refresh())
                .bounds(width / 2 - 50, height - 30, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("完成"), _ -> onClose())
                .bounds(width / 2 + 55, height - 30, 100, 20).build());

        configFileList.updateEntries();
        updateButtonStates();
    }

    private void reloadConfigs() {
        configs.clear();
        List<String> fileNames = new ArrayList<>();
        File[] files = WaypointListener.waypointFolder.listFiles((_, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) fileNames.add(file.getName());
            }
        }
        if (!fileNames.contains(WaypointListener.DEFAULT_FILE_NAME)) {
            fileNames.add(WaypointListener.DEFAULT_FILE_NAME);
        }
        fileNames.sort(Comparator.comparing((String name) -> !name.equals(WaypointListener.DEFAULT_FILE_NAME))
                .thenComparing(String.CASE_INSENSITIVE_ORDER));
        for (String fileName : fileNames) {
            configs.put(fileName, Waypoint.fromConfig(fileName));
        }
        if (!configs.containsKey(selectedFileName)) {
            selectedFileName = WaypointListener.DEFAULT_FILE_NAME;
        }
        syncAllConfigs();
    }

    private void syncAllConfigs() {
        WaypointListener.configFileToWaypointMap.clear();
        WaypointListener.configFileToWaypointMap.putAll(configs);
        AbstractListener.waypointListener.mergeFromConfig();
    }

    private void refresh() {
        reloadConfigs();
        configFileList.updateEntries();
    }

    private void selectConfig(String fileName) {
        selectedFileName = fileName;
        WaypointListener.currentEditingFileName = fileName;
        waypointList.updateEntries();
        updateButtonStates();
    }

    private List<Waypoint> selectedWaypoints() {
        return configs.computeIfAbsent(selectedFileName, _ -> new ArrayList<>());
    }

    private void saveSelectedConfig() {
        List<Waypoint> waypoints = selectedWaypoints();
        Waypoint.saveToConfig(selectedFileName, waypoints);
        WaypointListener.configFileToWaypointMap.put(selectedFileName, waypoints);
        AbstractListener.waypointListener.mergeFromConfig();
    }

    private void openNewConfigEditor() {
        minecraft.gui.setScreen(new ConfigNameEditorScreen("新建配置文件", "", name -> {
            configs.put(name, new ArrayList<>());
            selectedFileName = name;
            Waypoint.saveToConfig(name, List.of());
            syncAllConfigs();
        }, name -> isConfigNameAvailable(name, null)));
    }

    private void openRenameConfigEditor() {
        if (selectedFileName == null || selectedFileName.equals(WaypointListener.DEFAULT_FILE_NAME)) return;
        String oldName = selectedFileName;
        minecraft.gui.setScreen(new ConfigNameEditorScreen("重命名配置文件", oldName, newName -> {
            try {
                Files.move(new File(WaypointListener.waypointFolder, oldName).toPath(),
                        new File(WaypointListener.waypointFolder, newName).toPath());
                selectedFileName = newName;
                reloadConfigs();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, name -> isConfigNameAvailable(name, oldName)));
    }

    private boolean isConfigNameAvailable(String fileName, String originalName) {
        if (!isValidConfigFileName(fileName)) return false;
        if (fileName.equals(originalName)) return true;
        return !configs.containsKey(fileName) && !new File(WaypointListener.waypointFolder, fileName).exists();
    }

    private static String normalizeConfigFileName(String input) {
        String fileName = input.trim();
        return fileName.endsWith(".json") ? fileName : fileName + ".json";
    }

    private static boolean isValidConfigFileName(String fileName) {
        String baseName = fileName.substring(0, fileName.length() - 5);
        return !baseName.isBlank() && !baseName.equals(".") && !baseName.equals("..")
                && baseName.matches("[^\\\\/:*?\"<>|]+");
    }

    private void confirmDeleteConfig() {
        if (selectedFileName == null || selectedFileName.equals(WaypointListener.DEFAULT_FILE_NAME)) return;
        String fileName = selectedFileName;
        minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                try {
                    Files.deleteIfExists(new File(WaypointListener.waypointFolder, fileName).toPath());
                    selectedFileName = WaypointListener.DEFAULT_FILE_NAME;
                    reloadConfigs();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            minecraft.gui.setScreen(this);
        }, Component.literal("删除配置文件"), Component.literal("确定删除 " + fileName + " 吗？此操作无法撤销。")));
    }

    private void openWaypointEditor(Waypoint waypoint) {
        if (selectedFileName == null) return;
        minecraft.gui.setScreen(new WaypointEditorScreen(waypoint));
    }

    private void editSelectedWaypoint() {
        WaypointEntry entry = waypointList.getSelected();
        if (entry != null) openWaypointEditor(entry.waypoint);
    }

    private void toggleSelectedEnabled() {
        WaypointEntry entry = waypointList.getSelected();
        if (entry == null) return;
        List<Waypoint> waypoints = selectedWaypoints();
        int index = waypoints.indexOf(entry.waypoint);
        if (index < 0) return;
        Waypoint waypoint = waypoints.get(index);
        waypoints.set(index, new Waypoint(waypoint.id(), !waypoint.enabled(), waypoint.ordered(), waypoint.displayTitle(), waypoint.pos(),
                waypoint.color(), waypoint.scorebroadCondition(), waypoint.drawBox(), waypoint.showDistance(),
                waypoint.isFromConfig(), waypoint.removeIfWorldChange(), waypoint.keepTimeMills()));
        saveSelectedConfig();
        waypointList.updateEntries(index);
    }

    private void toggleAllEnabled() {
        List<Waypoint> waypoints = selectedWaypoints();
        if (waypoints.isEmpty()) return;
        WaypointEntry selectedEntry = waypointList.getSelected();
        int selectedIndex = selectedEntry == null ? -1 : waypointList.children().indexOf(selectedEntry);
        boolean targetEnabled = !waypoints.stream().allMatch(Waypoint::enabled);
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint waypoint = waypoints.get(i);
            if (waypoint.enabled() == targetEnabled) continue;
            waypoints.set(i, new Waypoint(waypoint.id(), targetEnabled, waypoint.ordered(), waypoint.displayTitle(), waypoint.pos(),
                    waypoint.color(), waypoint.scorebroadCondition(), waypoint.drawBox(), waypoint.showDistance(),
                    waypoint.isFromConfig(), waypoint.removeIfWorldChange(), waypoint.keepTimeMills()));
        }
        saveSelectedConfig();
        waypointList.updateEntries(selectedIndex);
    }

    private void saveWaypoint(Waypoint original, Waypoint edited) {
        List<Waypoint> waypoints = selectedWaypoints();
        if (original == null) {
            waypoints.add(edited);
        } else {
            for (int i = 0; i < waypoints.size(); i++) {
                if (waypoints.get(i) == original) {
                    waypoints.set(i, edited);
                    break;
                }
            }
        }
        saveSelectedConfig();
    }

    private void confirmDeleteWaypoint() {
        WaypointEntry entry = waypointList.getSelected();
        if (entry == null) return;
        Waypoint waypoint = entry.waypoint;
        minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                selectedWaypoints().removeIf(candidate -> candidate == waypoint);
                saveSelectedConfig();
            }
            minecraft.gui.setScreen(this);
        }, Component.literal("删除路径点"), Component.literal("确定删除路径点「" + waypoint.displayTitle() + "」吗？")));
    }

    private int nextWaypointId() {
        return selectedWaypoints().stream().mapToInt(Waypoint::id).max().orElse(0) + 1;
    }

    private double distanceSqr(Waypoint waypoint) {
        BlockPos playerPos = minecraft.player != null ? minecraft.player.blockPosition() : BlockPos.ZERO;
        return playerPos.distSqr(waypoint.pos());
    }

    private void updateButtonStates() {
        if (deleteConfigButton == null) return;
        boolean hasConfig = selectedFileName != null;
        boolean defaultSelected = WaypointListener.DEFAULT_FILE_NAME.equals(selectedFileName);
        renameConfigButton.active = hasConfig && !defaultSelected;
        deleteConfigButton.active = hasConfig && !defaultSelected;
        addWaypointButton.active = hasConfig;
        toggleAllEnabledButton.active = hasConfig && !selectedWaypoints().isEmpty();
        boolean hasWaypoint = waypointList != null && waypointList.getSelected() != null;
        editWaypointButton.active = hasWaypoint;
        toggleEnabledButton.active = hasWaypoint;
        deleteWaypointButton.active = hasWaypoint;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    private class ConfigFileList extends ObjectSelectionList<ConfigFileEntry> {

        private ConfigFileList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        private void updateEntries() {
            clearEntries();
            ConfigFileEntry selectedEntry = null;
            for (String fileName : configs.keySet()) {
                ConfigFileEntry entry = new ConfigFileEntry(fileName);
                addEntry(entry);
                if (fileName.equals(selectedFileName)) selectedEntry = entry;
            }
            setSelected(selectedEntry);
        }

        @Override
        public void setSelected(ConfigFileEntry entry) {
            super.setSelected(entry);
            selectConfig(entry == null ? null : entry.fileName);
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

    private class ConfigFileEntry extends ObjectSelectionList.Entry<ConfigFileEntry> {

        private final String fileName;

        private ConfigFileEntry(String fileName) {
            this.fileName = fileName;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
            String displayName = fileName.equals(WaypointListener.DEFAULT_FILE_NAME) ? "默认配置 (_default.json)" : fileName;
            graphics.centeredText(font, displayName, getContentXMiddle(), getY() + 7, 0xFFFFFFFF);
        }

        @Override
        public Component getNarration() {
            return Component.literal(fileName);
        }
    }

    private class WaypointList extends ObjectSelectionList<WaypointEntry> {

        private WaypointList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        private void updateEntries() {
            clearEntries();
            if (selectedFileName != null) {
                List<Waypoint> sorted = new ArrayList<>(selectedWaypoints());
                sorted.sort(Comparator.comparingDouble(WaypointSettingsScreen.this::distanceSqr));
                for (int i = 0; i < sorted.size(); i++) {
                    addEntry(new WaypointEntry(sorted.get(i), i == 0));
                }
            }
            setSelected(null);
        }

        private void updateEntries(int selectIndex) {
            updateEntries();
            if (selectIndex >= 0 && selectIndex < children().size()) {
                setSelected(children().get(selectIndex));
            }
        }

        @Override
        public void setSelected(WaypointEntry entry) {
            super.setSelected(entry);
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

    private class WaypointEntry extends ObjectSelectionList.Entry<WaypointEntry> {

        private final Waypoint waypoint;
        private final boolean nearest;

        private WaypointEntry(Waypoint waypoint, boolean nearest) {
            this.waypoint = waypoint;
            this.nearest = nearest;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
            String title = waypoint.displayTitle().isBlank() ? "<未命名>" : waypoint.displayTitle();
            graphics.text(font, Component.literal(title).withColor(waypoint.color().getRGB()), getContentX(), getY() + 2, 0xFFFFFFFF);
            int offsetX = getContentX() + font.width(title) + 4;
            if (!waypoint.enabled()) {
                graphics.text(font, "[已禁用]", offsetX, getY() + 2, 0xFF888888);
                offsetX += font.width("[已禁用]") + 4;
            }
            if (nearest) {
                graphics.text(font, "(离你最近)", offsetX, getY() + 2, 0xFF00FF00);
            }
            graphics.text(font, "#" + waypoint.id(), getContentRight() - font.width("#" + waypoint.id()), getY() + 2, 0xFFAAAAAA);
            BlockPos pos = waypoint.pos();
            String details = "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")"
                    + (waypoint.scorebroadCondition().isEmpty() ? "" : "  条件: " + waypoint.scorebroadCondition());
            graphics.text(font, details, getContentX(), getY() + 16, 0xFFAAAAAA);
        }

        @Override
        public Component getNarration() {
            return Component.literal(waypoint.displayTitle());
        }
    }

    private class ConfigNameEditorScreen extends Screen {

        private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
        private final String initialName;
        private final Consumer<String> onSave;
        private final Predicate<String> validator;
        private EditBox nameBox;
        private Button saveButton;

        private ConfigNameEditorScreen(String title, String initialName, Consumer<String> onSave, Predicate<String> validator) {
            super(Component.literal(title));
            this.initialName = initialName;
            this.onSave = onSave;
            this.validator = validator;
        }

        @Override
        protected void init() {
            layout.addTitleHeader(title, font);
            nameBox = new EditBox(font, 200, 20, Component.literal("配置文件名"));
            nameBox.setMaxLength(100);
            nameBox.setHint(Component.literal("配置文件名"));
            nameBox.setResponder(_ -> validateName());
            layout.addToContents(nameBox);
            LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
            saveButton = footer.addChild(Button.builder(Component.literal("保存"), _ -> save()).size(80, 20).build());
            footer.addChild(Button.builder(Component.literal("取消"), _ -> onClose()).size(80, 20).build());
            layout.visitWidgets(this::addRenderableWidget);
            nameBox.setValue(initialName);
            layout.arrangeElements();
            validateName();
        }

        private void validateName() {
            boolean valid = validator.test(normalizeConfigFileName(nameBox.getValue()));
            nameBox.setTextColor(valid ? 0xFFFFFFFF : 0xFFFF5555);
            if (saveButton != null) saveButton.active = valid;
        }

        private void save() {
            String fileName = normalizeConfigFileName(nameBox.getValue());
            if (!validator.test(fileName)) return;
            onSave.accept(fileName);
            minecraft.gui.setScreen(WaypointSettingsScreen.this);
        }

        @Override
        public void onClose() {
            minecraft.gui.setScreen(WaypointSettingsScreen.this);
        }
    }

    private class WaypointEditorScreen extends Screen {

        private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 33, 33);
        private final Waypoint original;
        private EditBox titleBox;
        private EditBox idBox;
        private EditBox xBox;
        private EditBox yBox;
        private EditBox zBox;
        private EditBox conditionBox;
        private Button saveButton;
        private Button drawBoxButton;
        private Button showDistanceButton;
        private Button orderedButton;
        private XSDSliderButton redSlider;
        private int red;
        private int green;
        private int blue;
        private boolean drawBox;
        private boolean showDistance;
        private boolean ordered;

        private WaypointEditorScreen(Waypoint original) {
            super(Component.literal(original == null ? "添加路径点" : "编辑路径点"));
            this.original = original;
        }

        @Override
        protected void init() {
            Waypoint waypoint = original != null ? original : createDefaultWaypoint();
            red = waypoint.color().getRed();
            green = waypoint.color().getGreen();
            blue = waypoint.color().getBlue();
            drawBox = waypoint.drawBox();
            showDistance = waypoint.showDistance();
            ordered = waypoint.ordered();

            layout.addTitleHeader(title, font);
            LinearLayout form = layout.addToContents(LinearLayout.vertical().spacing(4));
            form.defaultCellSetting().alignHorizontallyCenter();

            LinearLayout titleRow = form.addChild(LinearLayout.horizontal().spacing(4));
            titleBox = createBox(titleRow, 200, "路径点标题");
            idBox = createBox(titleRow, 100, "ID (顺序化用)");

            LinearLayout colorRow = form.addChild(LinearLayout.horizontal().spacing(8));
            LinearLayout sliders = colorRow.addChild(LinearLayout.vertical().spacing(4));
            redSlider = sliders.addChild(new XSDSliderButton(0, 0, 200, 20, Component.empty(), 0)
                    .stringMsgGetter(() -> "红色: " + red)
                    .valueSetter(value -> red = (int) Math.round(value * 255))
                    .valueGetter(() -> red / 255.0));
            sliders.addChild(new XSDSliderButton(0, 0, 200, 20, Component.empty(), 0)
                    .stringMsgGetter(() -> "绿色: " + green)
                    .valueSetter(value -> green = (int) Math.round(value * 255))
                    .valueGetter(() -> green / 255.0));
            sliders.addChild(new XSDSliderButton(0, 0, 200, 20, Component.empty(), 0)
                    .stringMsgGetter(() -> "蓝色: " + blue)
                    .valueSetter(value -> blue = (int) Math.round(value * 255))
                    .valueGetter(() -> blue / 255.0));
            colorRow.addChild(new SpacerElement(48, 68));

            LinearLayout positionRow = form.addChild(LinearLayout.horizontal().spacing(4));
            xBox = createBox(positionRow, 55, "X");
            yBox = createBox(positionRow, 55, "Y");
            zBox = createBox(positionRow, 55, "Z");

            LinearLayout conditionRow = form.addChild(LinearLayout.horizontal().spacing(4));
            conditionBox = createBox(conditionRow, 304, "计分板条件，留空始终显示");
            conditionBox.setTooltip(Tooltip.create(Component.literal("留空始终显示, 如果填入内容则计分板需要包含对应文本才会显示, 适用于特定区域显示. 如果要同时在多个区域显示, 使用|分割以匹配任意一个文本")));

            LinearLayout optionRow = form.addChild(LinearLayout.horizontal().spacing(4));
            drawBoxButton = optionRow.addChild(Button.builder(drawBoxText(), _ -> {
                drawBox = !drawBox;
                drawBoxButton.setMessage(drawBoxText());
            }).size(98, 20).build());
            drawBoxButton.setTooltip(Tooltip.create(Component.literal("是否绘制该路径点的方块描边")));
            showDistanceButton = optionRow.addChild(Button.builder(showDistanceText(), _ -> {
                showDistance = !showDistance;
                showDistanceButton.setMessage(showDistanceText());
            }).size(98, 20).build());
            showDistanceButton.setTooltip(Tooltip.create(Component.literal("是否在路径点标签上显示距离")));
            orderedButton = optionRow.addChild(Button.builder(orderedText(), _ -> {
                ordered = !ordered;
                orderedButton.setMessage(orderedText());
            }).size(98, 20).build());
            orderedButton.setTooltip(Tooltip.create(Component.literal("顺序模式: 开启后该路径点加入所在配置文件的队列, 按 ID 从小到大依次引导; 同一时间只渲染当前目标与下一个路径点(绿色描边为当前目标, 黄色描边为下一个), 走近当前目标 3 格内自动切换到下一个, 走完整条队列后从头循环")));

            LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
            saveButton = footer.addChild(Button.builder(Component.literal("保存"), _ -> save()).size(80, 20).build());
            footer.addChild(Button.builder(Component.literal("取消"), _ -> onClose()).size(80, 20).build());

            titleBox.setValue(waypoint.displayTitle());
            idBox.setValue(Integer.toString(waypoint.id()));
            xBox.setValue(Integer.toString(waypoint.pos().getX()));
            yBox.setValue(Integer.toString(waypoint.pos().getY()));
            zBox.setValue(Integer.toString(waypoint.pos().getZ()));
            conditionBox.setValue(waypoint.scorebroadCondition());

            layout.visitWidgets(this::addRenderableWidget);
            layout.arrangeElements();
            validateInputs();
        }

        private EditBox createBox(LinearLayout row, int width, String hint) {
            EditBox box = new EditBox(font, width, 20, Component.literal(hint));
            box.setHint(Component.literal(hint));
            box.setTooltip(Tooltip.create(Component.literal(hint)));
            box.setMaxLength(hint.equals("路径点标题") || hint.startsWith("计分板") ? 1000 : 32);
            box.setResponder(_ -> validateInputs());
            row.addChild(box);
            return box;
        }

        private Waypoint createDefaultWaypoint() {
            BlockPos pos = minecraft.player != null ? minecraft.player.blockPosition() : BlockPos.ZERO;
            return new Waypoint(nextWaypointId(), true, false, "新路径点", pos, Color.WHITE, "", true, true, true, false, 0);
        }

        private Component orderedText() {
            return Component.literal("顺序: " + (ordered ? "§a是" : "§c否"));
        }

        private Component drawBoxText() {
            return Component.literal("方框: " + (drawBox ? "§a是" : "§c否"));
        }

        private Component showDistanceText() {
            return Component.literal("距离: " + (showDistance ? "§a是" : "§c否"));
        }

        private void validateInputs() {
            if (saveButton == null) return;
            boolean titleValid = !titleBox.getValue().isBlank();
            boolean idValid = parseInt(idBox) != null;
            boolean xValid = parseInt(xBox) != null;
            boolean yValid = parseInt(yBox) != null;
            boolean zValid = parseInt(zBox) != null;
            setValid(titleBox, titleValid);
            setValid(idBox, idValid);
            setValid(xBox, xValid);
            setValid(yBox, yValid);
            setValid(zBox, zValid);
            saveButton.active = titleValid && idValid && xValid && yValid && zValid;
        }

        private void save() {
            validateInputs();
            if (!saveButton.active) return;
            Waypoint edited = new Waypoint(
                    parseInt(idBox),
                    original == null || original.enabled(),
                    ordered,
                    titleBox.getValue(),
                    new BlockPos(parseInt(xBox), parseInt(yBox), parseInt(zBox)),
                    new Color(red, green, blue),
                    conditionBox.getValue(),
                    drawBox,
                    showDistance,
                    true,
                    false,
                    0
            );
            saveWaypoint(original, edited);
            minecraft.gui.setScreen(WaypointSettingsScreen.this);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            super.extractRenderState(graphics, mouseX, mouseY, delta);
            if (redSlider == null) return;
            int previewX = redSlider.getX() + redSlider.getWidth() + 8;
            int previewY = redSlider.getY() + 10;
            graphics.fill(previewX, previewY, previewX + 48, previewY + 48, new Color(red, green, blue).getRGB());
            graphics.outline(previewX, previewY, 48, 48, 0xFFFFFFFF);
        }

        @Override
        public void onClose() {
            minecraft.gui.setScreen(WaypointSettingsScreen.this);
        }
    }

    private static void setValid(EditBox box, boolean valid) {
        box.setTextColor(valid ? 0xFFFFFFFF : 0xFFFF5555);
    }

    private static Integer parseInt(EditBox box) {
        try {
            return Integer.valueOf(box.getValue().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
