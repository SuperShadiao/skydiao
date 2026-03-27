package pers.XiaoShadiao.skydiao.screen;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.EditGameRulesScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.config.option.*;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager.translate;

public class ConfigScreen extends Screen {

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final TabManager tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
    private TabNavigationBar tabNavigationBar;

    private final Screen lastScreen;

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
        super(Component.literal("SkyDiao Mod"));
        this.lastScreen = lastScreen;
    }

    @Override
    public void tick() {
        if(delayedSearchHandler != null) {
            delayedSearchHandler.run();
            delayedSearchHandler = null;
        }
        super.tick();
    }

    @Override
    protected void init() {
        tabs = getTabs();
        this.tabNavigationBar = TabNavigationBar.builder(this.tabManager, this.width)
                .addTabs(tabs)
                .build();
        this.addRenderableWidget(this.tabNavigationBar);
        this.tabNavigationBar.selectTab(0, false);

        LinearLayout linearLayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        linearLayout.addChild(Button.builder(Component.literal("§eQQ | Discord"), (button) -> Util.getPlatform().openUri("https://xiaoshadiao.club/about")).size(100, 20).build());
        linearLayout.addChild(Button.builder(Component.literal("§6" + translate("gui.configsettings.buttonsaveback")), this::onClose).size(150, 20).build());
        linearLayout.addChild(Button.builder(Component.literal("§a" + translate("gui.configsettings.buttonfriendlink")), (button) -> Util.getPlatform().openUri("https://xiaoshadiao.club/friendlinks")).size(100, 20).build());

        this.layout.visitWidgets(abstractWidget -> {
            abstractWidget.setTabOrderGroup(1);
            this.addRenderableWidget(abstractWidget);
        });
        this.repositionElements();
    }

    @Override
    public void repositionElements() {
        if (this.tabNavigationBar != null) {
            this.tabNavigationBar.setWidth(this.width);
            this.tabNavigationBar.arrangeElements();
            int i = this.tabNavigationBar.getRectangle().bottom();
            ScreenRectangle screenRectangle = new ScreenRectangle(0, i, this.width, this.height - this.layout.getFooterHeight() - i);
            this.tabManager.setTabArea(screenRectangle);
            this.layout.setHeaderHeight(i);
            this.layout.arrangeElements();
            if(tabs != null) {
                for (Tab tab : tabs) {
                    ((ConfigTab) tab).updateConfigList();
                }
            }
        }
    }

    private Tab[] getTabs() {
        List<ConfigTab> list = ConfigManager.categories.stream().map(entry -> new ConfigTab(entry.getKey(), entry.getValue())).collect(Collectors.toList());
        list.add(new SearchConfigTab());
        if(ToolList.getInstance().isDevEnvironment()) {
            List<ConfigOption<?, ?>> test = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                switch(ToolList.getInstance().random.nextInt(5)) {
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
                    default:
                        test.add(new SelectConfigOption("test" + i, 0, List.of("a", "b", "c")));
                        break;
                }
            }
            list.add(new ConfigTab("test", test));
        }
        return list.toArray(Tab[]::new);
    }

    public abstract static class AbstractConfigEntry extends ContainerObjectSelectionList.Entry<AbstractConfigEntry> {}

    public class SearchConfigTab extends ConfigTab {

        public SearchConfigTab() {
            super("搜索", ConfigManager.optionList);
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
                searchBox.setValue("");
                searchBox.setMaxLength(1000);
                searchBox.setResponder(this::updateSearchResult);
                updateSearchResult("");
            }

            private void updateSearchResult(String text) {
                Optional<SearchConfigEntry> searchEntry = children().stream().filter(child -> child instanceof SearchConfigEntry).map(child -> (SearchConfigEntry) child).findFirst();

                clearEntries();
                String text1 = text.trim();
                addEntryToTop(searchEntry.orElseGet(SearchConfigEntry::new));

                delayedSearchHandler = () -> {
                    for (Tab tab : tabs) {
                        if(tab instanceof SearchConfigTab) continue;
                        for (AbstractConfigEntry child0 : ((ConfigTab) tab).configList.children()) {
                            if(child0 instanceof ConfigEntry child) {
                                if(text1.isEmpty() || child.option.getI18nName().toLowerCase().contains(text1.toLowerCase()) || child.option.getI18nDesc().toLowerCase().contains(text.toLowerCase())) {
                                    addEntry(new RedirectConfigEntry(child));
                                }
                            }
                        }
                    }
                };

            }

            public class SearchConfigEntry extends AbstractConfigEntry {
                @Override
                public @NotNull List<? extends GuiEventListener> children() {
                    return List.of(searchBox);
                }

                @Override
                public void renderContent(GuiGraphics guiGraphics, int left, int top, boolean bl, float f) {
                    searchBox.setX(getContentRight() - (getContentWidth() + searchBox.getWidth()) / 2);
                    searchBox.setY(getContentY());
                    searchBox.render(guiGraphics, left, top, f);
                }

                @Override
                public @NotNull List<? extends NarratableEntry> narratables() {
                    return List.of(searchBox);
                }
            }

            public class RedirectConfigEntry extends AbstractConfigEntry {

                private final Button widget;
                private final ConfigEntry configEntry;

                public RedirectConfigEntry(ConfigEntry configEntry) {
                    this.configEntry = configEntry;
                    widget = Button.builder(Component.literal("->"), this::redirect).size(70, 20).build();
                }

                private void redirect(Button button) {
                    delayedSearchHandler = () -> {
                        int tabIndex = List.of(tabs).indexOf(configEntry.tab);
                        tabNavigationBar.selectTab(tabIndex, true);
                        configEntry.tab.configList.setSelected(configEntry);
                        ConfigScreen.this.setFocused(configEntry.widget);
                    };
                }

                @Override
                public @NotNull List<? extends NarratableEntry> narratables() {
                    return List.of(widget);
                }

                @Override
                public void renderContent(GuiGraphics guiGraphics, int left, int top, boolean bl, float f) {
                    String name = configEntry.option.getI18nName();
                    // guiGraphics.drawString(Minecraft.getInstance().font, name, getContentX() - 5, getContentY() + 5, 0xFFFFFFFF);
                    RenderUtils.renderScrollingString(guiGraphics, ToolList.mc.font, Component.literal(name), getContentX(), getContentX(), getContentY() - 25, getContentX() + 100, getContentY() + 44, 0xFFFFFFFF);
                    widget.setX(getContentRight() - widget.getWidth() + 5);
                    widget.setY(getContentY());
                    widget.render(guiGraphics, left, top, f);
                }

                @Override
                public @NotNull List<? extends GuiEventListener> children() {
                    return List.of(widget);
                }
            }
        }
    }


    public class ConfigTab extends GridLayoutTab {
        public final List<ConfigOption<?, ?>> configOptions;
        public ConfigList configList;

        public ConfigTab(String categoryName, List<ConfigOption<?, ?>> configOptions) {
            super(Component.literal(translate("configcategory." + categoryName)));
            this.configOptions = configOptions;
            configList = getListInstance();
            this.layout.addChild(configList,0,0);
        }

        public ConfigList getListInstance() {
            return new ConfigList(this);
        }

        public void updateConfigList() {
            // configList.setX(20);
//            configList.setY(ConfigScreen.this.layout.getHeaderHeight());
//            configList.setWidth(ConfigScreen.this.width);
//            configList.setHeight(ConfigScreen.this.layout.getContentHeight());

            configList.updateSize(ConfigScreen.this.width, ConfigScreen.this.layout);
        }

        public class ConfigList extends ContainerObjectSelectionList<AbstractConfigEntry> {

            public final ConfigTab tab;

            public ConfigList(ConfigTab tab) {
                super(Minecraft.getInstance(), ConfigScreen.this.width, ConfigScreen.this.layout.getContentHeight(), ConfigScreen.this.layout.getHeaderHeight(), 20);
                for (ConfigOption<?, ?> configOption : configOptions) {
                    addEntry(new ConfigEntry(configOption, tab));
                }
                this.tab = tab;
            }

            @Override
            public int getRowWidth() {
                return 305;
            }

            public static class ConfigEntry extends AbstractConfigEntry {
                private final ConfigOption<?, ?> option;
                private final AbstractWidget widget;
                private final ConfigTab tab;
                public ConfigEntry(ConfigOption<?, ?> option, ConfigTab tab) {
                    this.option = option;
                    this.tab = tab;
                    this.widget = switch(option) {
                        case BooleanConfigOption boolOption -> Button.builder(
                                Component.literal(boolOption.getI18nValue()),
                                b -> {
                                    boolOption.setValue(!boolOption.getValue());
                                    b.setMessage(Component.literal(boolOption.getI18nValue()));
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
                        case StringConfigOption stringOption -> {
                            EditBox editBox = new EditBox(ToolList.mc.font, 0, 0, 100, 20, Component.literal(stringOption.getI18nName()));
                            editBox.setMaxLength(1000);
                            editBox.setValue(stringOption.getValue());
                            editBox.setResponder(stringOption::setValue);
                            yield editBox;
                        }
                        default -> throw new UnsupportedOperationException(option.getClass().getName());
                    };
                    MutableComponent component = Component.literal(option.getI18nDesc());
                    if(option.isMacroFeature()) {
                        component.append("\n\n");
                        component.append(translate("config.macrofeaturealert"));
                    }
                    widget.setTooltip(Tooltip.create(component));
                }

                @Override
                public @NotNull List<? extends NarratableEntry> narratables() {
                    return List.of(widget);
                }

                @Override
                public void renderContent(GuiGraphics guiGraphics, int left, int top, boolean bl, float f) {
                    String name = option.getI18nName();
                    // guiGraphics.drawString(Minecraft.getInstance().font, name, getContentX() - 5, getContentY() + 5, 0xFFFFFFFF);
                    RenderUtils.renderScrollingString(guiGraphics, ToolList.mc.font, Component.literal(name), getContentX(), getContentX(), getContentY() - 25, getContentX() + 100, getContentY() + 44, 0xFFFFFFFF);
                    widget.setX(getContentRight() - widget.getWidth() + 5);
                    widget.setY(getContentY());
                    widget.render(guiGraphics, left, top, f);
                }

                @Override
                public @NotNull List<? extends GuiEventListener> children() {
                    return List.of(widget);
                }
            }

        }

    }

}
