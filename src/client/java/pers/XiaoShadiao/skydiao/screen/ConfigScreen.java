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
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.config.option.*;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager.translate;

public class ConfigScreen extends Screen {

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final TabManager tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
    private TabNavigationBar tabNavigationBar;

    private final Screen lastScreen;

    private Tab[] tabs;

    private void saveConfig() {
        ConfigManager.saveConfig();
        CrowdinI18nManager.initI18nFromConfig();
    }

    @Override
    public void onClose() {
        saveConfig();
        ToolList.mc.setScreen(lastScreen);
    }

    private void onClose(Button button) {
        onClose();
    }

    public ConfigScreen(Screen lastScreen) {
        super(Component.literal("SkyDiao Mod"));
        this.lastScreen = lastScreen;
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
        if(ToolList.getInstance().isDevEnvironment()) {
            List<ConfigOption<?>> test = new ArrayList<>();
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

    public class ConfigTab extends GridLayoutTab {
        public final List<ConfigOption<?>> configOptions;
        public ConfigList configList;

        public ConfigTab(String categoryName, List<ConfigOption<?>> configOptions) {
            super(Component.literal(translate("configcategory." + categoryName)));
            this.configOptions = configOptions;
            configList = new ConfigList();
            this.layout.addChild(configList,0,0);
        }

        public void updateConfigList() {
            // configList.setX(20);
//            configList.setY(ConfigScreen.this.layout.getHeaderHeight());
//            configList.setWidth(ConfigScreen.this.width);
//            configList.setHeight(ConfigScreen.this.layout.getContentHeight());

            configList.updateSize(ConfigScreen.this.width, ConfigScreen.this.layout);
        }

        public class ConfigList extends ContainerObjectSelectionList<ConfigList.ConfigEntry> {

            public ConfigList() {
                super(Minecraft.getInstance(), ConfigScreen.this.width, ConfigScreen.this.layout.getContentHeight(), ConfigScreen.this.layout.getHeaderHeight(), 20);
                for (ConfigOption<?> configOption : configOptions) {
                    addEntry(new ConfigEntry(configOption));
                }
            }

            @Override
            public int getRowWidth() {
                return 305;
            }

            public static class ConfigEntry extends ContainerObjectSelectionList.Entry<ConfigEntry> {
                private final ConfigOption<?> option;
                private final AbstractWidget widget;
                public ConfigEntry(ConfigOption<?> option) {
                    this.option = option;
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
                            editBox.setValue(stringOption.getValue());
                            editBox.setResponder(stringOption::setValue);
                            yield editBox;
                        }
                        default -> throw new UnsupportedOperationException(option.getClass().getName());
                    };
                    widget.setTooltip(Tooltip.create(Component.literal(option.getI18nDesc())));
                }

                @Override
                public @NotNull List<? extends NarratableEntry> narratables() {
                    return List.of(widget);
                }

                @Override
                public void renderContent(GuiGraphics guiGraphics, int left, int top, boolean bl, float f) {
                    String name = translate("config." + option.getName() + ".configname");
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
