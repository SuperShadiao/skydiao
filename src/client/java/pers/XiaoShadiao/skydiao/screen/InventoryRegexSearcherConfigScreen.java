package pers.XiaoShadiao.skydiao.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.List;

import static pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager.translate;

public class InventoryRegexSearcherConfigScreen extends Screen {

    public static Component getTitle0() {
        return Component.literal(translate("skydiao.gui.itemfilter.title"));
    }

    public InventoryRegexSearcherConfigScreen(Screen parent) {
        super(getTitle0());
        this.parent = parent;
    }

    private static RegexList regexList;
    private LinearLayout footerButtonLayout;
    private final Screen parent;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    @Override
    public void onClose() {
        JsonArray ja = new JsonArray();
        for (AbstractEntry child0 : regexList.children()) {
            if(child0 instanceof RegexList.RegexEntry regex) {
                ja.add(regex.editBox.getValue());
            }
        }
        ConfigManager.inventoryFilterRegexList.setValue(ja.toString());
        ConfigManager.saveConfig();
        // super.onClose();
        ToolList.mc.setScreen(parent);
    }

    @Override
    protected void init() {
        LinearLayout linearLayout = footerButtonLayout =  this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        linearLayout.addChild(LinearLayout.horizontal().spacing(8));
        linearLayout.addChild(Button.builder(Component.literal(translate("skydiao.gui.itemfilter.buttonback")), b -> onClose()).build());
        linearLayout.setX(width / 2);
        regexList = new RegexList();
        this.layout.addToContents(regexList);
        this.layout.addTitleHeader(this.title, this.font);
        this.layout.visitWidgets(abstractWidget -> {
            abstractWidget.setTabOrderGroup(1);
            this.addRenderableWidget(abstractWidget);
        });
        this.repositionElements();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int i, int j, float f) {
        super.render(guiGraphics, i, j, f);
    }

    @Override
    public void repositionElements() {
        this.layout.arrangeElements();
        if(regexList != null) {
            regexList.updateSize(InventoryRegexSearcherConfigScreen.this.width, InventoryRegexSearcherConfigScreen.this.layout);
        }
    }

    public abstract static class AbstractEntry extends ContainerObjectSelectionList.Entry<AbstractEntry> {}

    public class RegexList extends ContainerObjectSelectionList<AbstractEntry> {

        public RegexList() {
            super(Minecraft.getInstance(), InventoryRegexSearcherConfigScreen.this.width, InventoryRegexSearcherConfigScreen.this.layout.getContentHeight(), InventoryRegexSearcherConfigScreen.this.layout.getHeaderHeight(), 30);
            try {
                String value = ConfigManager.inventoryFilterRegexList.getValue();
                addEntry(new AddOperationEntry());
                for (JsonElement je : JsonParser.parseString(value).getAsJsonArray()) {
                    addEntry(new RegexEntry(je.getAsString()));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public class RegexEntry extends AbstractEntry {
            private final EditBox editBox;
            private final Button apply;
            private final Button delete;
            private final List<AbstractWidget> childs;

            public RegexEntry(String str) {
                this.editBox = new EditBox(ToolList.mc.font, getContentX(), getContentY(), getContentRight() - getContentX() - 110, 20, Component.literal(""));
                this.editBox.setMaxLength(Integer.MAX_VALUE);
                this.editBox.setValue(str);
                this.editBox.moveCursorToStart(false);

                this.apply = Button.builder(
                        Component.literal(translate("skydiao.gui.itemfilter.buttonapply")),
                        b -> {
                            ConfigManager.inventoryFilterRegex.setValue(editBox.getValue());
                            ConfigManager.saveConfig();
                            onClose();
                        }
                ).bounds(50, 0, 50, 20).build();
                this.delete = Button.builder(
                        Component.literal(translate("skydiao.gui.itemfilter.buttondelete")),
                        b -> {
                            RegexList.this.removeEntry(this);
                        }
                ).bounds(0, 0, 50, 20).build();
                this.childs = List.of(editBox, apply, delete);
            }

            @Override
            public @NotNull List<? extends NarratableEntry> narratables() {
                return childs;
            }

            @Override
            public void renderContent(GuiGraphics guiGraphics, int left, int top, boolean bl, float f) {
                apply.setX(getContentRight() - apply.getWidth() - 50);
                apply.setY(getContentY());
                apply.render(guiGraphics, left, top, f);

                delete.setX(getContentRight() - delete.getWidth());
                delete.setY(getContentY());
                delete.render(guiGraphics, left, top, f);

                editBox.setX(getContentX());
                editBox.setY(getContentY());
                editBox.setWidth(getContentRight() - getContentX() - 110);
                editBox.setHeight(20);
                editBox.render(guiGraphics, left, top, f);
            }

            @Override
            public @NotNull List<? extends GuiEventListener> children() {
                return childs;
            }
        }

        public class AddOperationEntry extends AbstractEntry {

            private final Button add;
            private final List<AbstractWidget> childs;

            public AddOperationEntry() {
                add = Button.builder(
                        Component.literal(translate("skydiao.gui.itemfilter.buttonaddplan")),
                        (b) -> {
                            addEntry(new RegexEntry(""));
                        }
                ).bounds(0, 0, 70, 20).build();
                childs = List.of(add);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return childs;
            }

            @Override
            public void renderContent(GuiGraphics guiGraphics, int left, int top, boolean bl, float f) {
                add.setX(getContentX() + (getContentRight() - getContentX() - add.getWidth()) / 2);
                add.setY(getContentY());
                add.render(guiGraphics, left, top, f);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return childs;
            }
        }

        @Override
        protected void removeEntry(AbstractEntry entry) {
            super.removeEntry(entry);
        }

        public List<AbstractEntry> getEntries() {
            return children();
        }
    }
}