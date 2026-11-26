package pers.XiaoShadiao.skydiao.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.quiver.QuiverTracker;

import java.util.Locale;

/**
 * 当前箭矢类型和数量的 HUD，参考 SkyHanni 的 Quiver Display。
 */
public class QuiverHUD extends XSDHUD {

    @Override
    public void runRegister() {
        QuiverTracker.init();
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("skydiao", "quiver_hud"), this);
    }

    @Override
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter, boolean force) {
        if (!ConfigManager.quiverHUD.getValue() || !ToolList.isInSkyblock()) {
            return;
        }
        if (mc.player == null || !QuiverTracker.shouldShow()) {
            return;
        }

        String type = QuiverTracker.getArrowType();
        if (type.isEmpty()) {
            return;
        }

        int amount = QuiverTracker.getArrowAmount();
        boolean hideAmount = QuiverTracker.isWearingSkeletonMaster() || !QuiverTracker.isAmountKnown();
        int x = context.guiWidth() / 2 + 50;
        int y = context.guiHeight() / 2 + 70;
        int textX = x;
        int textY = y + 4;

        if (ConfigManager.quiverHUDShowIcon.getValue()) {
            ItemStack icon = QuiverTracker.getArrowIcon();
            context.item(mc.player, icon, x, y, 0);
            textX = x + 18;
        }

        String displayName = type.equalsIgnoreCase("NONE") || hideAmount ? type : pluralize(type, amount);
        int arrowColor = QuiverTracker.getArrowColor();
        if (hideAmount) {
            context.text(mc.font, displayName, textX, textY, arrowColor, true);
        } else {
            String amountText = format(amount) + "x ";
            context.text(mc.font, amountText, textX, textY, 0xFF55FFFF, true);
            context.text(mc.font, displayName, textX + mc.font.width(amountText), textY, arrowColor, true);
        }
    }

    private static String pluralize(String name, int amount) {
        if (amount == 1 || name.endsWith("s")) {
            return name;
        }
        return name + "s";
    }

    private static String format(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    @Override
    public void renderEffect(GuiGraphicsExtractor context, DeltaTracker tickCounter, boolean force, HudOffsetAndScale settings) {
    }

    @Nullable
    @Override
    public String getHudName() {
        return "ArrowHUD";
    }
}
