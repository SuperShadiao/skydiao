package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.screen.InventoryRegexSearcherConfigScreen;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager.translate;

public class InventoryItemFilter extends AbstractListener {

    private boolean enabled;
    private final List<String> hints = List.of("搜索物品", "可使用正则表达式", "可使用\\n分割以创建多个搜索条件, 用于跨行搜索");

    @Override
    public String getListenerName() {
        return "InventoryItemFilter";
    }

    @Override
    public void registerListeners() {
        ScreenEvents.AFTER_INIT.register(this::afterScreenInit);
        CustomFabricEvents.ON_SLOT_RENDER.register(this::renderSlots);
    }

    private EditBox filterTextBox;

    private void afterScreenInit(Minecraft mc, Screen screen, int scaledWidth, int scaledHeight) {
        if(!ConfigManager.inventoryFilter.getValue()) return;
        if(!(screen instanceof AbstractContainerScreen)) return;
        if(screen instanceof CreativeModeInventoryScreen) return;

        // ScreenEvents.afterExtract(screen).register(this::postRender);
        ScreenKeyboardEvents.allowKeyPress(screen).register(this::allowKeyPress);
        ScreenMouseEvents.afterMouseClick(screen).register(this::postMouseClick);
        ScreenEvents.remove(screen).register((screen2) -> {
            ConfigManager.inventoryFilterRegex.setValue(filterTextBox.getValue());
            ConfigManager.saveConfig();
        });

        List<AbstractWidget> buttons = Screens.getWidgets(screen);
        filterTextBox = new EditBox(mc.font, 0, 0, Component.literal("搜索框"));
        filterTextBox.setWidth(100);
        filterTextBox.setHeight(20);
        filterTextBox.setHint(Component.literal("输入搜索内容..."));
        filterTextBox.setX(10);
        filterTextBox.setY(screen.height / 2 - 20);
        filterTextBox.setMaxLength(Integer.MAX_VALUE);
        filterTextBox.setValue(ConfigManager.inventoryFilterRegex.getValue());
        filterTextBox.setTooltip(Tooltip.create(Component.literal(String.join(", ", hints))));

        buttons.add(filterTextBox);
        buttons.add(Button.builder(Component.literal(translate("skydiao.gui.itemfilter.buttonentrance")), (button) -> mc.setScreen(new InventoryRegexSearcherConfigScreen(mc.screen))).bounds(10, screen.height / 2, 80, 20).build());
        MutableComponent enabledCom = Component.literal("§a■");
        MutableComponent disabledCom = Component.literal("§c■");
        buttons.add(Button.builder(enabled ? enabledCom : disabledCom, (button) -> button.setMessage((enabled = !enabled) ? enabledCom : disabledCom)).bounds(10 + 80, screen.height / 2, 20, 20).build());
    }

    private boolean postMouseClick(Screen screen, MouseButtonEvent mouseButtonEvent, boolean b) {
        if (filterTextBox.isFocused() && !filterTextBox.mouseClicked(mouseButtonEvent, false)) {
            screen.setFocused(null);
        }
        return false;
    }

    private boolean allowKeyPress(Screen screen, KeyEvent keyEvent) {
        if(filterTextBox.isFocused()) {
            filterTextBox.keyPressed(keyEvent);
            if(keyEvent.isEscape()) {
                screen.setFocused(null);
            }
            return false;
        }
        return true;
    }

    public void renderSlots(Screen screen, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float tickDelta) {
        if(!enabled || !(screen instanceof AbstractContainerScreen<?> abstractContainerScreen)) return;

        AbstractContainerMenu menu = abstractContainerScreen.getMenu();
        if(ToolList.getInstance().stringHasContext(filterTextBox.getValue().trim())) {
            for (Slot slot : menu.slots) {
                if (slot.getItem().getItem() == Items.AIR) {
                    RenderUtils.renderSlot(guiGraphics, abstractContainerScreen, slot, new Color(0, 0, 0, 150).getRGB());
                    continue;
                }
                if (matchItems(slot)) {
                    RenderUtils.renderSlot(guiGraphics, abstractContainerScreen, slot, new Color(255, 128, 0, 150).getRGB());
                } else {
                    RenderUtils.renderSlot(guiGraphics, abstractContainerScreen, slot, new Color(0, 0, 0, 150).getRGB());
                }
            }
        }
    }

    private boolean matchItems(Slot slot) {
        try {
            String trim = filterTextBox.getValue().trim();
            String[] value0 = trim.split("\\\\n");
            String[] value = trim.toLowerCase().split("\\\\n");

            List<Component> list = slot.getItem().getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.Default.NORMAL);
            boolean flag = false;
            flag |= Arrays.stream(value).allMatch(v -> list.stream().anyMatch(c ->ToolList.getInstance().deleteColorCode(c.getString()).toLowerCase().contains(v)));
            if(!flag) {
                try {
//                    Pattern pattern = Pattern.compile(value0);
//                    flag = list.stream().anyMatch(c -> {
//                        String s = ToolList.getInstance().deleteColorCode(c.getString());
//                        return pattern.matcher(s).find();
//                    });
                    flag = Arrays.stream(value0).map(Pattern::compile).allMatch(pattern -> list.stream().anyMatch(c -> pattern.matcher(ToolList.getInstance().deleteColorCode(c.getString())).find()));
                } catch (Exception ignored) {}
            }

            return flag;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

}
