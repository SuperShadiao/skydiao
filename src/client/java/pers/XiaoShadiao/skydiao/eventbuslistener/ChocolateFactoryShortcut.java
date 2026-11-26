package pers.XiaoShadiao.skydiao.eventbuslistener;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.mixin.client.MixinAbstractContainerScreenPosGetter;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.List;

public class ChocolateFactoryShortcut extends AbstractListener {

    private static final int SHORTCUT_SLOT = 16; // Second row, eighth column.
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
        if (!hasShortcut(screen, false)) return;

        Slot slot = screen.getMenu().getSlot(SHORTCUT_SLOT);

        if (ToolList.getInstance().isDevEnvironment() || icon == null || icon.isEmpty()) {
            icon = new ItemStack(Items.COOKIE);
            icon.set(DataComponents.CUSTOM_NAME, Component.literal("§6Chocolate Factory"));
            List<Component> tooltip = List.of(Component.literal("Click to enter Chocolate Factory"), Component.empty(), Component.literal("§7Button From SkyDiao"));
            icon.set(DataComponents.LORE, new ItemLore(tooltip, tooltip));
        }
        if(slot.getItem().is(Items.STAINED_GLASS_PANE.black())) slot.set(icon);
    }

    private boolean allowMouseClick(ContainerScreen screen, MouseButtonEvent event) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT
                || !screen.getMenu().getCarried().isEmpty() || !hasShortcut(screen, true)) return true;

        Slot slot = screen.getMenu().getSlot(SHORTCUT_SLOT);
        MixinAbstractContainerScreenPosGetter position = (MixinAbstractContainerScreenPosGetter) screen;
        if (!isOverSlot(event.x(), event.y(), position.getLeftPos() + slot.x, position.getTopPos() + slot.y)) {
            return true;
        }

        ToolList.sendChatMessage("/chocolatefactory");
        return false;
    }

    private boolean hasShortcut(ContainerScreen screen, boolean detectIcon) {
        if (!ConfigManager.chocolateFactoryShortcut.getValue() || !StatusManager.get().isInSkyblock()) return false;

        ItemStack item = screen.getMenu().getSlot(SHORTCUT_SLOT).getItem();
        return detectIcon ? item == icon : item.isEmpty() || (item.is(Items.STAINED_GLASS_PANE.black()) && item.getHoverName().getString().isBlank());
    }

    private boolean isOverSlot(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17;
    }
}
