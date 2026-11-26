package pers.XiaoShadiao.skydiao.eventbuslistener;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.mixin.client.MixinAbstractContainerScreenPosGetter;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.List;

public class ChocolateFactoryShortcut extends AbstractListener {

    private static final int SHORTCUT_SLOT = 16; // Second row, eighth column.
    private static final Component TOOLTIP = Component.literal("Enter Chocolate Factory");
    private ItemStack icon;

    @Override
    public String getListenerName() {
        return "ChocolateFactoryShortcut";
    }

    @Override
    public void registerListeners() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof ContainerScreen container)
                    || container.getMenu().getRowCount() != 6
                    || !"SkyBlock Menu".equals(screen.getTitle().getString())) return;

            ScreenEvents.afterExtract(screen).register((ignored, graphics, mouseX, mouseY, delta) ->
                    renderShortcut(container, graphics, mouseX, mouseY));
            ScreenMouseEvents.allowMouseClick(screen).register((ignored, event) -> allowMouseClick(container, event));
        });
    }

    private void renderShortcut(ContainerScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!hasShortcut(screen)) return;

        Slot slot = screen.getMenu().getSlot(SHORTCUT_SLOT);
        MixinAbstractContainerScreenPosGetter position = (MixinAbstractContainerScreenPosGetter) screen;
        int x = position.getLeftPos() + slot.x;
        int y = position.getTopPos() + slot.y;
        if (icon == null) icon = new ItemStack(Items.COOKIE);
        graphics.nextStratum();
        graphics.fakeItem(icon, x, y);
        if (isOverSlot(mouseX, mouseY, x, y) && screen.getMenu().getCarried().isEmpty()) {
            graphics.setTooltipForNextFrame(mc.font, List.of(TOOLTIP.getVisualOrderText()),
                    DefaultTooltipPositioner.INSTANCE, mouseX, mouseY, true);
        }
    }

    private boolean allowMouseClick(ContainerScreen screen, MouseButtonEvent event) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT
                || !screen.getMenu().getCarried().isEmpty() || !hasShortcut(screen)) return true;

        Slot slot = screen.getMenu().getSlot(SHORTCUT_SLOT);
        MixinAbstractContainerScreenPosGetter position = (MixinAbstractContainerScreenPosGetter) screen;
        if (!isOverSlot(event.x(), event.y(), position.getLeftPos() + slot.x, position.getTopPos() + slot.y)) {
            return true;
        }

        screen.onClose();
        ToolList.sendChatMessage("/chocolatefactory");
        return false;
    }

    private boolean hasShortcut(ContainerScreen screen) {
        if (!ConfigManager.chocolateFactoryShortcut.getValue() || !StatusManager.get().isInSkyblock()) return false;

        ItemStack item = screen.getMenu().getSlot(SHORTCUT_SLOT).getItem();
        return item.isEmpty() || (item.is(Items.STAINED_GLASS_PANE.black()) && item.getHoverName().getString().isBlank());
    }

    private boolean isOverSlot(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17;
    }
}
