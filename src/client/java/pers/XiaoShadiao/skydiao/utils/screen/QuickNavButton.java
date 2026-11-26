package pers.XiaoShadiao.skydiao.utils.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.mixin.client.MixinAbstractContainerScreenPosGetter;
import pers.XiaoShadiao.skydiao.utils.ToolList;

/**
 * 仿创造模式物品栏选项卡的快捷导航按钮。
 */
public class QuickNavButton extends AbstractWidget {

    private static final long TOGGLE_DURATION = 1000;
    private static final long DOUBLE_CLICK_TIME = 1000;
    private static final int WIDTH = 26;
    private static final int HEIGHT = 32;

    private final AbstractContainerScreen<?> parent;
    private final int index;
    private final boolean selected;
    private final String command;
    private final ItemStack icon;
    private final boolean doubleClick;
    private final Component originalTooltip;

    private boolean temporaryToggled;
    private boolean showingConfirmTooltip;
    private long toggleTime;
    private long lastClickTime;
    private int alpha = 255;

    public QuickNavButton(AbstractContainerScreen<?> parent, int index, boolean selected,
                          String command, ItemStack icon, String tooltip, boolean doubleClick) {
        super(0, 0, WIDTH, HEIGHT, Component.empty());
        this.parent = parent;
        this.index = index;
        this.selected = selected;
        this.command = command;
        this.icon = icon;
        this.doubleClick = doubleClick;
        this.originalTooltip = tooltip == null || tooltip.isEmpty() ? null : Component.literal(tooltip);
        if (this.originalTooltip != null) {
            setTooltip(Tooltip.create(this.originalTooltip));
        }
    }

    public boolean isActive0() {
        return selected || temporaryToggled;
    }

    /**
     * Widget 本身只负责更新坐标和接收输入，实际绘制由 ScreenEvents 的
     * afterBackground（未选中）与 afterForeground（选中）分两层完成。
     */
    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        updateCoordinates();
    }

    public void renderTab(GuiGraphicsExtractor graphics) {
        updateCoordinates();

        long now = System.currentTimeMillis();
        if (lastClickTime != 0 && now - lastClickTime > DOUBLE_CLICK_TIME) {
            lastClickTime = 0;
            restoreTooltip();
        }
        if (temporaryToggled && now - toggleTime >= TOGGLE_DURATION) {
            temporaryToggled = false;
        }
        if (alpha < 255) {
            alpha = Math.min(alpha + 10, 255);
        }

        boolean active = isActive0();
        Identifier texture = tabTexture(active);
        int tint = active ? ARGB.color(alpha, -1) : -1;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, texture, getX(), getY(), WIDTH, HEIGHT, tint);

        if (ToolList.mc.player != null) {
            int yOffset = index < 7 ? 1 : -1;
            graphics.item(ToolList.mc.player, icon, getX() + 5, getY() + 8 + yOffset, 0);
        }

        handleCursor(graphics);
    }

    private void updateCoordinates() {
        MixinAbstractContainerScreenPosGetter accessor = (MixinAbstractContainerScreenPosGetter) parent;
        int x = accessor.getLeftPos();
        int y = accessor.getTopPos();
        int width = accessor.getImageWidth();
        int height = accessor.getImageHeight();
        if (parent instanceof ContainerScreen) {
            height--;
        }

        setX(x + index % 7 * 25 + width / 2 - 176 / 2);
        setY(index < 7 ? y - 28 : y + height - 4);
    }

    private Identifier tabTexture(boolean active) {
        String side = index < 7 ? "top" : "bottom";
        String state = active ? "selected" : "unselected";
        int number = index % 7 + 1;
        return Identifier.withDefaultNamespace("container/creative_inventory/tab_" + side + "_" + state + "_" + number);
    }

    @Override
    public void onClick(MouseButtonEvent click, boolean doubled) {
        if (doubleClick) {
            long now = System.currentTimeMillis();
            if (lastClickTime == 0 || now - lastClickTime > DOUBLE_CLICK_TIME) {
                lastClickTime = now;
                setTooltip(Tooltip.create(Component.literal("再点击一次以确认")));
                showingConfirmTooltip = true;
                return;
            }
            lastClickTime = 0;
            restoreTooltip();
        }

        if (temporaryToggled) {
            return;
        }
        temporaryToggled = true;
        toggleTime = System.currentTimeMillis();
        alpha = 0;

        if (command == null || command.isEmpty()) {
            return;
        }
        AbstractListener.delayTickExecutor.delayExec(() -> {
            // ToolList.mc.gui.setScreen(null);
            ToolList.sendChatMessage(command);
        }, 1);
    }

    private void restoreTooltip() {
        if (showingConfirmTooltip) {
            setTooltip(originalTooltip == null ? null : Tooltip.create(originalTooltip));
            showingConfirmTooltip = false;
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
    }
}
