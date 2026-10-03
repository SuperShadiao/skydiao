package pers.XiaoShadiao.skydiao.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.config.ConfigOptionTree;
import pers.XiaoShadiao.skydiao.config.option.*;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;
import pers.XiaoShadiao.skydiao.utils.screen.XSDSliderButton;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager.translate;

public class ConfigScreen extends Screen {

    private HeaderAndFooterLayout layout;
    private TabManager tabManager;
    private TabNavigationBar tabNavigationBar;

    private final Screen lastScreen;
    private final ConfigGroupOption group;
    private int selectedTabIndex;
    private double[] scrollPositions = new double[0];
    private String searchText = "";
    private ConfigOption<?> pendingFocus;

    private Tab[] tabs;

    private Runnable delayedSearchHandler;

    private void saveConfig() {
        ConfigManager.saveConfig();
        CrowdinI18nManager.initI18nFromConfig();
    }

    @Override
    public void onClose() {
        saveConfig();
        ToolList.mc.setScreen(lastScreen);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
        boolean b = super.mouseClicked(mouseButtonEvent, bl);
        simulateScroll();
        return b;
    }

    private void simulateScroll() {
        double i = ToolList.mc.mouseHandler.getScaledXPos(ToolList.mc.getWindow());
        double j = ToolList.mc.mouseHandler.getScaledYPos(ToolList.mc.getWindow());
        this.mouseScrolled(i, j + 40, 0, 0);
    }

    private void onClose(Button button) {
        onClose();
    }

    public ConfigScreen(Screen lastScreen) {
        this(lastScreen, null);
    }

    public ConfigScreen(Screen lastScreen, ConfigGroupOption group) {
        super(Component.literal(group == null ? "SkyDiao Mod" : group.getI18nName()));
        this.lastScreen = lastScreen;
        this.group = group;
    }

    @Override
    public void removed() {
        if (tabs != null && tabManager != null) {
            selectedTabIndex = Math.max(0, List.of(tabs).indexOf(tabManager.getCurrentTab()));
            scrollPositions = new double[tabs.length];
            for (int i = 0; i < tabs.length; i++) {
                scrollPositions[i] = ((ConfigTab) tabs[i]).configList.scrollAmount();
            }
        }
        super.removed();
    }

    private List<ConfigOption<?>> scopedOptions() {
        return group == null
                ? ConfigManager.categories.stream().flatMap(entry -> entry.getValue().stream()).toList()
                : group.getOptions();
    }

    private void navigateTo(ConfigTab tab, ConfigOptionTree.Entry destination) {
        ConfigScreen target = this;
        for (ConfigGroupOption parent : destination.parents()) {
            target = new ConfigScreen(target, parent);
        }
        if (destination.option() instanceof ConfigGroupOption childGroup) {
            target = new ConfigScreen(target, childGroup);
        } else if (target == this) {
            focusOption(tab, destination.option());
            return;
        } else {
            target.pendingFocus = destination.option();
        }
        ToolList.mc.setScreen(target);
    }

    private void focusOption(ConfigTab tab, ConfigOption<?> option) {
        if (tabNavigationBar != null) {
            tabNavigationBar.selectTab(List.of(tabs).indexOf(tab), true);
        }
        tab.configList.focusOption(option);
        setFocused(tab.configList);
    }

    @Override
    public void tick() {
        if(delayedSearchHandler != null) {
            Runnable handler = delayedSearchHandler;
            delayedSearchHandler = null;
            handler.run();
        }
        super.tick();
    }

    @Override
    protected void init() {
        // A parent screen is reused on return. Recreate layouts so footer widgets do not accumulate.
        layout = new HeaderAndFooterLayout(this);
        tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
        tabs = getTabs();
        if (group == null) {
            this.tabNavigationBar = TabNavigationBar.builder(this.tabManager, this.width)
                    .addTabs(tabs)
                    .build();
            this.addRenderableWidget(this.tabNavigationBar);
            this.tabNavigationBar.selectTab(Math.min(selectedTabIndex, tabs.length - 1), false);
        } else {
            this.tabNavigationBar = null;
            layout.addTitleHeader(title, font);
            tabManager.setCurrentTab(tabs[0], false);
        }

        LinearLayout linearLayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));

        String disableKey = group == null ? "disableall" : "disablepage";
        linearLayout.addChild(Button.builder(Component.literal("§c" + translate("gui.configsettings." + disableKey)), (button) -> {
            minecraft.setScreen(new ConfirmScreen(result -> {
                if(result) {
                    ConfigOptionTree.disableAll(scopedOptions());
                    saveConfig();
                }
                minecraft.setScreen(this);
                if (result) rebuildWidgets();
            }, Component.literal("§c" + translate("gui.configsettings." + disableKey)), Component.literal("§c" + translate("gui.configsettings." + disableKey + "confirm"))));
        }).size(100, 20).build());

        if (group == null) {
            linearLayout.addChild(Button.builder(Component.literal("§eQQ | Discord"), (button) -> Util.getPlatform().openUri("https://xiaoshadiao.club/about")).size(100, 20).build());
        }
        linearLayout.addChild(Button.builder(Component.literal("§6" + translate("gui.configsettings.buttonsaveback")), this::onClose).size(100, 20).build());
        if (group == null) {
            linearLayout.addChild(Button.builder(Component.literal("§a" + translate("gui.configsettings.buttonfriendlink")), (button) -> Util.getPlatform().openUri("https://xiaoshadiao.club/friendlinks")).size(100, 20).build());
        }

        String resetKey = group == null ? "resetalltodefault" : "resetpagetodefault";
        linearLayout.addChild(Button.builder(Component.literal("§c" + translate("gui.configsettings." + resetKey)), (button) -> {
            minecraft.setScreen(new ConfirmScreen(result -> {
                if(result) {
                    ConfigOptionTree.resetAll(scopedOptions());
                    saveConfig();
                }
                minecraft.setScreen(this);
                if (result) rebuildWidgets();
            }, Component.literal("§c" + translate("gui.configsettings." + resetKey)), Component.literal("§c" + translate("gui.configsettings." + resetKey + "confirm"))));
        }).size(100, 20).build());

        this.layout.visitWidgets(abstractWidget -> {
            abstractWidget.setTabOrderGroup(1);
            this.addRenderableWidget(abstractWidget);
        });
        this.repositionElements();
        for (int i = 0; i < Math.min(tabs.length, scrollPositions.length); i++) {
            if (!(tabs[i] instanceof SearchConfigTab)) {
                ((ConfigTab) tabs[i]).configList.setScrollAmount(scrollPositions[i]);
            }
        }
        if (pendingFocus != null) {
            ConfigOption<?> option = pendingFocus;
            delayedSearchHandler = () -> focusOption((ConfigTab) tabs[0], option);
            pendingFocus = null;
        }
    }

    @Override
    public void repositionElements() {
        if (layout == null || tabManager == null) return;
        if (this.tabNavigationBar != null) {
            this.tabNavigationBar.updateWidth(this.width);
            this.tabNavigationBar.arrangeElements();
            int i = this.tabNavigationBar.getRectangle().bottom();
            this.layout.setHeaderHeight(i);
        }
        this.layout.arrangeElements();
        this.tabManager.setTabArea(new ScreenRectangle(0, layout.getHeaderHeight(), this.width, layout.getContentHeight()));
        if(tabs != null) {
            for (Tab tab : tabs) {
                ((ConfigTab) tab).updateConfigList();
            }
        }
    }

    private Tab[] getTabs() {
        if (group != null) {
            return new Tab[]{new ConfigTab(title, group.getOptions())};
        }
        List<ConfigTab> list = ConfigManager.categories.stream().map(entry -> new ConfigTab(entry.getKey(), entry.getValue())).collect(Collectors.toList());
        list.add(new SearchConfigTab());
        if(ToolList.getInstance().isDevEnvironment()) {
            List<ConfigOption<?>> test = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                switch(ToolList.getInstance().random.nextInt(6)) {
                    case 0:
                        test.add(new BooleanConfigOption("test" + i, true));
                        break;
                    case 1:
                        test.add(new BooleanConfigOption("test" + i, false));
                        break;
                    case 2:
                        test.add(new IntConfigOption("test" + i, 0));
                        break;
                    case 3:
                        test.add(new StringConfigOption("test" + i, "awa"));
                        break;
                    case 4:
                        test.add(new SelectConfigOption("test" + i, 0, List.of("a", "b", "c")));
                        break;
                    case 5:
                        test.add(new ColorConfigOption("test" + i, new Color(ToolList.getInstance().random.nextInt(256), ToolList.getInstance().random.nextInt(256), ToolList.getInstance().random.nextInt(256)).getRGB()));
                        break;
                    default:
                }
            }
            list.add(new ConfigTab("test", test));
        }
        return list.toArray(Tab[]::new);
    }

    public abstract static class AbstractConfigEntry extends ContainerObjectSelectionList.Entry<AbstractConfigEntry> {}

    public class SearchConfigTab extends ConfigTab {

        public SearchConfigTab() {
            super("搜索", List.of());
        }

        @Override
        public ConfigList getListInstance() {
            return new SearchConfigList();
        }

        public class SearchConfigList extends ConfigTab.ConfigList {
            private final EditBox searchBox;
            public SearchConfigList() {
                super(SearchConfigTab.this);
                searchBox = new EditBox(ToolList.mc.font, 0, 0, 200, 20, Component.literal(translate("configcategory.搜索")));
                searchBox.setValue(searchText);
                searchBox.setMaxLength(1000);
                searchBox.setResponder(this::updateSearchResult);
                updateSearchResult(searchText);
            }

            private void updateSearchResult(String text) {
                Optional<SearchConfigEntry> searchEntry = children().stream().filter(child -> child instanceof SearchConfigEntry).map(child -> (SearchConfigEntry) child).findFirst();

                clearEntries();
                boolean restoreScroll = text.equals(searchText);
                searchText = text;
                String query = text.trim().toLowerCase(Locale.ROOT);
                addEntryToTop(searchEntry.orElseGet(SearchConfigEntry::new));

                delayedSearchHandler = () -> {
                    for (Tab tab : tabs) {
                        if(tab instanceof SearchConfigTab) continue;
                        ConfigTab configTab = (ConfigTab) tab;
                        for (ConfigOptionTree.Entry entry : ConfigOptionTree.entries(configTab.configOptions)) {
                            ConfigOption<?> option = entry.option();
                            if (query.isEmpty() || option.getI18nName().toLowerCase(Locale.ROOT).contains(query)
                                    || option.getI18nDesc().toLowerCase(Locale.ROOT).contains(query)) {
                                addEntry(new RedirectConfigEntry(configTab, entry));
                            }
                        }
                    }
                    if (restoreScroll) {
                        int index = List.of(tabs).indexOf(SearchConfigTab.this);
                        if (index >= 0 && index < scrollPositions.length) setScrollAmount(scrollPositions[index]);
                    }
                };

            }

            public class SearchConfigEntry extends AbstractConfigEntry {
                @Override
                public @NotNull List<? extends GuiEventListener> children() {
                    return List.of(searchBox);
                }

                @Override
                public void extractContent(GuiGraphicsExtractor guiGraphics, int left, int top, boolean bl, float f) {
                    searchBox.setX(getContentRight() - (getContentWidth() + searchBox.getWidth()) / 2);
                    searchBox.setY(getContentY());
                    searchBox.extractRenderState(guiGraphics, left, top, f);
                }

                @Override
                public @NotNull List<? extends NarratableEntry> narratables() {
                    return List.of(searchBox);
                }
            }

            public class RedirectConfigEntry extends AbstractConfigEntry {

                private final Button widget;
                private final ConfigTab destinationTab;
                private final ConfigOptionTree.Entry destination;

                public RedirectConfigEntry(ConfigTab destinationTab, ConfigOptionTree.Entry destination) {
                    this.destinationTab = destinationTab;
                    this.destination = destination;
                    widget = Button.builder(Component.literal("->"), this::redirect).size(70, 20).build();
                }

                private void redirect(Button button) {
                    delayedSearchHandler = () -> navigateTo(destinationTab, destination);
                }

                @Override
                public @NotNull List<? extends NarratableEntry> narratables() {
                    return List.of(widget);
                }

                @Override
                public void extractContent(GuiGraphicsExtractor guiGraphics, int left, int top, boolean bl, float f) {
                    String name = destination.option().getI18nName();
                    // guiGraphics.drawString(Minecraft.getInstance().font, name, getContentX() - 5, getContentY() + 5, 0xFFFFFFFF);
                    RenderUtils.renderScrollingString(guiGraphics, ToolList.mc.font, Component.literal(name), getContentX(), getContentX(), getContentY() - 25, getContentX() + 100, getContentY() + 44, 0xFFFFFFFF);
                    widget.setX(getContentRight() - widget.getWidth() + 5);
                    widget.setY(getContentY());
                    widget.extractRenderState(guiGraphics, left, top, f);
                }

                @Override
                public @NotNull List<? extends GuiEventListener> children() {
                    return List.of(widget);
                }
            }
        }
    }


    public class ConfigTab extends GridLayoutTab {
        public final List<ConfigOption<?>> configOptions;
        public ConfigList configList;

        public ConfigTab(String categoryName, List<ConfigOption<?>> configOptions) {
            this(Component.literal(translate("configcategory." + categoryName)), configOptions);
        }

        public ConfigTab(Component title, List<ConfigOption<?>> configOptions) {
            super(title);
            this.configOptions = configOptions;
            configList = getListInstance();
            this.layout.addChild(configList,0,0);
        }

        public ConfigList getListInstance() {
            return new ConfigList(this);
        }

        public void updateConfigList() {
            configList.updateSize(ConfigScreen.this.width, ConfigScreen.this.layout);
        }

        private void openGroup(ConfigGroupOption group) {
            ToolList.mc.setScreen(new ConfigScreen(ConfigScreen.this, group));
        }

        public class ConfigList extends ContainerObjectSelectionList<AbstractConfigEntry> {

            public final ConfigTab tab;

            public ConfigList(ConfigTab tab) {
                super(Minecraft.getInstance(), ConfigScreen.this.width, ConfigScreen.this.layout.getContentHeight(), ConfigScreen.this.layout.getHeaderHeight(), 20);
                for (ConfigOption<?> configOption : configOptions) {
                    addEntry(configOption instanceof ColorConfigOption colorConfigOption ? new ColorConfigEntry(colorConfigOption, tab) : new ConfigEntry(configOption, tab));
                }
                this.tab = tab;
            }

            private void focusOption(ConfigOption<?> option) {
                for (AbstractConfigEntry entry : children()) {
                    if (entry instanceof ConfigEntry configEntry && configEntry.option == option) {
                        setSelected(entry);
                        setFocused(entry);
                        configEntry.setFocused(configEntry.children().contains(configEntry.widget)
                                ? configEntry.widget : configEntry.children().getFirst());
                        centerScrollOn(entry);
                        return;
                    }
                }
            }

            @Override
            public int getRowWidth() {
                return 305;
            }

            public static class ConfigEntry extends AbstractConfigEntry {
                protected final ConfigOption<?> option;
                protected final AbstractWidget widget;
                protected final Button resetConfigButton;
                protected final ConfigTab tab;
                public ConfigEntry(ConfigOption<?> option, ConfigTab tab) {
                    this.option = option;
                    this.tab = tab;
                    this.widget = switch(option) {
                        case ConfigGroupOption groupOption -> Button.builder(
                                Component.literal(groupOption.getI18nValue()),
                                b -> tab.openGroup(groupOption)
                        ).bounds(0, 0, 100, 20).build();
                        case BooleanConfigOption boolOption -> Button.builder(
                                Component.literal(boolOption.getI18nValue()),
                                b -> {
                                    List<BooleanConfigOption> list = boolOption.getUsingThisFeatures().stream().filter(ConfigOption::getValue).toList();
                                    if(!list.isEmpty()) {
                                        ToolList.printChatMessage(Component.literal("§a[小沙雕] §c若要关闭功能§e" + boolOption.getI18nName() + "§c, 请先关闭功能§e" + list.stream().map(ConfigOption::getI18nName).collect(Collectors.joining("§c, §e"))));
                                    } else {
                                        boolOption.setValue(!boolOption.getValue());
                                        b.setMessage(Component.literal(boolOption.getI18nValue()));
                                    }
                                }
                        ).bounds(0, 0, 100, 20).build();
                        case SelectConfigOption selectOption -> Button.builder(
                                Component.literal(selectOption.getCurrentDisplayString()),
                                b -> {
                                    selectOption.switchOption();
                                    b.setMessage(Component.literal(selectOption.getCurrentDisplayString()));
                                }
                        ).bounds(0, 0, 100, 20).build();
                        case IntConfigOption intOption -> {
                            EditBox editBox = new EditBox(ToolList.mc.font, 0, 0, 100, 20, Component.literal(intOption.getI18nName()));
                            editBox.setValue(intOption.getValue().toString());
                            editBox.setResponder(stringx -> {
                                try {
                                    if(stringx.trim().isEmpty()) {
                                        editBox.setValue("0");
                                        intOption.setValue(0);
                                    } else {
                                        int i = Integer.parseInt(stringx);
                                        intOption.setValue(i);
                                    }
                                    editBox.setTextColor(-2039584);
                                } catch (NumberFormatException e) {
                                    editBox.setTextColor(-65536);
                                }
                            });
                            yield editBox;
                        }
                        case DoubleConfigOption doubleOption -> {
                            EditBox editBox = new EditBox(ToolList.mc.font, 0, 0, 100, 20, Component.literal(doubleOption.getI18nName()));
                            editBox.setValue(doubleOption.getValue().toString());
                            editBox.setResponder(stringx -> {
                                try {
                                    if(stringx.trim().isEmpty()) {
                                        editBox.setValue("0");
                                        doubleOption.setValue(0.0);
                                    } else {
                                        double d = Double.parseDouble(stringx);
                                        doubleOption.setValue(d);
                                    }
                                    editBox.setTextColor(-2039584);
                                } catch (NumberFormatException e) {
                                    editBox.setTextColor(-65536);
                                }
                            });
                            yield editBox;
                        }
                        case StringGetterSelectConfigOption stringGetterSelectConfigOption -> Button.builder(
                                Component.literal("->"),
                                (_)->Minecraft.getInstance().setScreenAndShow(
                                        new StringListScreen(stringGetterSelectConfigOption.getI18nName(),CrowdinI18nManager.translate("gui.stringlistscreen.subtitle")+stringGetterSelectConfigOption.getValue(),stringGetterSelectConfigOption))
                        ).bounds(0, 0, 100, 20).build();
                        case StringConfigOption stringOption -> {
                            EditBox editBox = new EditBox(ToolList.mc.font, 0, 0, 100, 20, Component.literal(stringOption.getI18nName()));
                            editBox.setMaxLength(1000);
                            editBox.setValue(stringOption.getValue());
                            editBox.setResponder(stringOption::setValue);
                            yield editBox;
                        }
                        case ActionConfigOption actionOption -> Button.builder(
                                Component.literal(actionOption.getI18nValue()),
                                actionOption::setValue
                        ).bounds(0, 0, 100, 20).build();
                        default -> throw new UnsupportedOperationException(option.getClass().getName());
                    };
                    resetConfigButton = Button.builder(
                            Component.literal("R"),
                            b -> option.resetToDefault()
                    ).bounds(0, 0, 20, 20).build();
                    resetConfigButton.visible = !(option instanceof ConfigGroupOption) && !(option instanceof ActionConfigOption);
                    MutableComponent component = Component.literal(option.getI18nDesc());
                    if(option.isMacroFeature()) {
                        component.append("\n\n");
                        component.append(translate("config.macrofeaturealert"));
                    }
                    if(option instanceof TimeDelayOption) {
                        component.append("\n\n");
                        component.append(translate("config.timedelaydesc"));
                    }
                    if(option.isForceDisabled()) {
                        widget.active = false;
                        component.append("\n\n");
                        component.append(translate("config.forcedisabled"));
                    }
                    widget.setTooltip(Tooltip.create(component));
                }

                @Override
                public @NotNull List<? extends NarratableEntry> narratables() {
                    return resetConfigButton.visible ? List.of(resetConfigButton, widget) : List.of(widget);
                }

                @Override
                public void extractContent(GuiGraphicsExtractor guiGraphics, int left, int top, boolean bl, float f) {
                    String name = option.getI18nName();
                    // guiGraphics.drawString(Minecraft.getInstance().font, name, getContentX() - 5, getContentY() + 5, 0xFFFFFFFF);
                    RenderUtils.renderScrollingString(guiGraphics, ToolList.mc.font, Component.literal(name), getContentX(), getContentX(), getContentY() - 25, getContentX() + 100, getContentY() + 44, 0xFFFFFFFF);
                    widget.setX(getContentRight() - widget.getWidth() + 5);
                    widget.setY(getContentY());
                    widget.extractRenderState(guiGraphics, left, top, f);

                    if (resetConfigButton.visible) {
                        resetConfigButton.active = !option.isDefaultValue();
                        resetConfigButton.setX(widget.getX() - resetConfigButton.getWidth());
                        resetConfigButton.setY(getContentY());
                        resetConfigButton.extractRenderState(guiGraphics, left, top, f);
                    }

                    if(widget instanceof Button button) {
                        button.setMessage(Component.literal(option.getI18nValue()));
                    } else if(widget instanceof EditBox editBox) {
                        String temp = option.getI18nValue();
                        if(!editBox.getValue().equals(temp) && !editBox.isFocused()) editBox.setValue(temp);
                    }
                }

                @Override
                public @NotNull List<? extends GuiEventListener> children() {
                    return resetConfigButton.visible ? List.of(resetConfigButton, widget) : List.of(widget);
                }
            }

            public static class ColorConfigEntry extends ConfigEntry {
                private final XSDSliderButton r;
                private final XSDSliderButton g;
                private final XSDSliderButton b;
                protected final Button resetConfigButton;
                private Color color;
                public ColorConfigEntry(ColorConfigOption colorOption, ConfigTab tab) {
                    super(colorOption, tab);

                    color = new Color(colorOption.getValue());
                    DoubleSupplier rSupplier = () -> color.getRed() / 255d;
                    DoubleSupplier gSupplier = () -> color.getGreen() / 255d;
                    DoubleSupplier bSupplier = () -> color.getBlue() / 255d;
                    DoubleConsumer rSetter = (r) -> colorOption.setValue((color = new Color((int) (r * 255), color.getGreen(), color.getBlue())).getRGB());
                    DoubleConsumer gSetter = (g) -> colorOption.setValue((color = new Color(color.getRed(), (int) (g * 255), color.getBlue())).getRGB());
                    DoubleConsumer bSetter = (b) -> colorOption.setValue((color = new Color(color.getRed(), color.getGreen(), (int) (b * 255))).getRGB());
                    Supplier<String> rMsg = () -> "§c" + color.getRed();
                    Supplier<String> gMsg = () -> "§a" + color.getGreen();
                    Supplier<String> bMsg = () -> "§1" + color.getBlue();

                    r = new XSDSliderButton(0, 0, 33, 20, Component.empty(), 1)
                            .valueGetter(rSupplier)
                            .valueSetter(rSetter)
                            .stringMsgGetter(rMsg);
                    g = new XSDSliderButton(0, 0, 33, 20, Component.empty(), 1)
                            .valueGetter(gSupplier)
                            .valueSetter(gSetter)
                            .stringMsgGetter(gMsg);
                    b = new XSDSliderButton(0, 0, 33, 20, Component.empty(), 1)
                            .valueGetter(bSupplier)
                            .valueSetter(bSetter)
                            .stringMsgGetter(bMsg);

                    resetConfigButton = Button.builder(
                            Component.literal("R"),
                            _ -> {
                                option.resetToDefault();
                                color = new Color(colorOption.getValue());
                                r.valueGetter(rSupplier);
                                g.valueGetter(gSupplier);
                                b.valueGetter(bSupplier);
                            }
                    ).bounds(0, 0, 20, 20).build();
                    MutableComponent component = Component.literal(option.getI18nDesc());
                    if(option.isMacroFeature()) {
                        component.append("\n\n");
                        component.append(translate("config.macrofeaturealert"));
                    }
                    if(option instanceof TimeDelayOption) {
                        component.append("\n\n");
                        component.append(translate("config.timedelaydesc"));
                    }
                    if(option.isForceDisabled()) {
                        r.active = false;
                        g.active = false;
                        b.active = false;
                        component.append("\n\n");
                        component.append(translate("config.forcedisabled"));
                    }
                    r.setTooltip(Tooltip.create(component));
                    g.setTooltip(Tooltip.create(component));
                    b.setTooltip(Tooltip.create(component));
                }

                @Override
                public @NotNull List<? extends NarratableEntry> narratables() {
                    return List.of(resetConfigButton, r, g, b);
                }

                @Override
                public void extractContent(GuiGraphicsExtractor guiGraphics, int left, int top, boolean bl, float f) {
                    String name = option.getI18nName();
                    // guiGraphics.drawString(Minecraft.getInstance().font, name, getContentX() - 5, getContentY() + 5, 0xFFFFFFFF);
                    RenderUtils.renderScrollingString(guiGraphics, ToolList.mc.font, Component.literal(name), getContentX(), getContentX(), getContentY() - 25, getContentX() + 100, getContentY() + 44, 0xFFFFFFFF);
                    r.setX(getContentRight() - r.getWidth() + 5 - 33 - 33);
                    r.setY(getContentY());
                    r.extractRenderState(guiGraphics, left, top, f);
                    g.setX(getContentRight() - g.getWidth() + 5 - 33);
                    g.setY(getContentY());
                    g.extractRenderState(guiGraphics, left, top, f);
                    b.setX(getContentRight() - b.getWidth() + 5);
                    b.setY(getContentY());
                    b.extractRenderState(guiGraphics, left, top, f);
                    resetConfigButton.active = !option.isDefaultValue();
                    resetConfigButton.setX(r.getX() - resetConfigButton.getWidth());
                    resetConfigButton.setY(getContentY());
                    resetConfigButton.extractRenderState(guiGraphics, left, top, f);
                    if(r.isHoveredOrFocused() || g.isHoveredOrFocused() || b.isHoveredOrFocused()) guiGraphics.fill(getContentRight() - r.getWidth() + 5 - 33 - 33 - 20 - 21, getContentY(), getContentRight() - r.getWidth() + 5 - 33 - 33 - 20 - 1, getContentY() + 20, color.getRGB());
                }

                @Override
                public @NotNull List<? extends GuiEventListener> children() {
                    return List.of(resetConfigButton, r, g, b);
                }
            }

        }

    }

}
