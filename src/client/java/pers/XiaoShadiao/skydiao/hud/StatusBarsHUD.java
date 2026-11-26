package pers.XiaoShadiao.skydiao.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.statusbar.StatusBarTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 自绘的血量、蓝量、防御、经验、速度、氧气和 Vitality 状态条。
 * 贴图与布局均为本项目独立实现，未使用 Skyblocker 的资源。
 */
public class StatusBarsHUD extends XSDHUD {

    private static final Identifier BAR_BACK = Identifier.fromNamespaceAndPath("skydiao", "bars/bar_back");
    private static final Identifier BAR_FILL = Identifier.fromNamespaceAndPath("skydiao", "bars/bar_fill");
    private static final Identifier HEALTH_ICON = Identifier.fromNamespaceAndPath("skydiao", "bars/icons/health");
    private static final Identifier MANA_ICON = Identifier.fromNamespaceAndPath("skydiao", "bars/icons/mana");
    private static final Identifier DEFENSE_ICON = Identifier.fromNamespaceAndPath("skydiao", "bars/icons/defense");
    private static final Identifier EXPERIENCE_ICON = Identifier.fromNamespaceAndPath("skydiao", "bars/icons/experience");
    private static final Identifier SPEED_ICON = Identifier.fromNamespaceAndPath("skydiao", "bars/icons/speed");
    private static final Identifier AIR_ICON = Identifier.fromNamespaceAndPath("skydiao", "bars/icons/air");
    private static final Identifier VITALITY_ICON = Identifier.fromNamespaceAndPath("skydiao", "bars/icons/vitality");

    private static final int HEALTH_COLOR = 0xFFFF4B4B;
    private static final int HEALTH_OVERFLOW_COLOR = 0xFFFFD84D;
    private static final int MANA_COLOR = 0xFF35D6FF;
    private static final int MANA_OVERFLOW_COLOR = 0xFFB368FF;
    private static final int DEFENSE_COLOR = 0xFFE8E8E8;
    private static final int EXPERIENCE_COLOR = 0xFF64E646;
    private static final int SPEED_COLOR = 0xFFE8E8E8;
    private static final int AIR_COLOR = 0xFF78D2FF;
    private static final int VITALITY_COLOR = 0xFFC83228;
    private static final int HEALTH_TEXT_COLOR = 0xFFFF8A8A;
    private static final int HEALTH_ABSORPTION_TEXT_COLOR = 0xFFFFD84D;
    private static final int MANA_TEXT_COLOR = 0xFF7FE6FF;
    private static final int MANA_OVERFLOW_TEXT_COLOR = 0xFFC79BFF;
    private static final int DEFENSE_TEXT_COLOR = 0xFFD8D8D8;
    private static final int EXPERIENCE_TEXT_COLOR = 0xFF8FE87A;
    private static final int SPEED_TEXT_COLOR = 0xFFD8D8D8;
    private static final int AIR_TEXT_COLOR = 0xFF8FD4FF;
    private static final int VITALITY_TEXT_COLOR = 0xFFE05A50;

    @Override
    public void runRegister() {
        StatusBarTracker.init();

        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
                Identifier.fromNamespaceAndPath("skydiao", "status_bars"), this);

        // 用自绘条替代原版血条、饥饿条、氧气条和经验条；关闭功能或不在 Skyblock 时保持原样
        hideWhenEnabled(VanillaHudElements.HEALTH_BAR);
        hideWhenEnabled(VanillaHudElements.FOOD_BAR);
        hideWhenEnabled(VanillaHudElements.AIR_BAR);
        hideWhenEnabled(VanillaHudElements.EXPERIENCE_LEVEL);
        hideWhenEnabled(VanillaHudElements.INFO_BAR);
    }

    private static void hideWhenEnabled(Identifier id) {
        HudElementRegistry.replaceElement(id, element -> (graphics, tick) -> {
            if (!isEnabled()) {
                element.extractRenderState(graphics, tick);
            }
        });
    }

    private static boolean isEnabled() {
        return ConfigManager.statusBars.getValue()
                && ToolList.isInSkyblock()
                && StatusBarTracker.hasHealthData();
    }

    @Override
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter, boolean force) {
        if (!isEnabled()) {
            return;
        }

        Player player = mc.player; 
        if (player == null) {
            return;
        }

        int screenWidth = context.guiWidth();
        int screenHeight = context.guiHeight();
        int left = screenWidth / 2 - 91;
        int top = screenHeight - 42;

        List<BarRender> bars = new ArrayList<>(7);
        List<CostPreview> costPreviews = new ArrayList<>(2);
        boolean showHealthMax = ConfigManager.statusBarsShowHealthMax.getValue();
        boolean showManaMax = ConfigManager.statusBarsShowManaMax.getValue();

        StatusBarTracker.Resource health = StatusBarTracker.getHealth();
        if (StatusBarTracker.hasHealthData()) {
            float fill = health.max() > 0 ? health.value() / (float) health.max() : 0;
            float overflow = health.max() > 0 ? health.overflow() / (float) health.max() : 0;
            String text = formatPlain(health.value());
            if (showHealthMax) {
                text += "/" + formatPlain(health.max());
            }
            int textColor = health.overflow() > 0 ? HEALTH_ABSORPTION_TEXT_COLOR : HEALTH_TEXT_COLOR;
            IconSide iconSide = IconSide.from(ConfigManager.statusBarHealthIcon.getValue());
            bars.add(new BarRender(left, top, 90, fill, overflow,
                    HEALTH_COLOR, HEALTH_OVERFLOW_COLOR, textColor, iconSide, HEALTH_ICON, text));
            addCostPreview(costPreviews, left, top, 90, iconSide,
                    health.value(), health.max(), StatusBarTracker.getHealthCost());
        }

        StatusBarTracker.Resource mana = StatusBarTracker.getMana();
        if (StatusBarTracker.hasManaData()) {
            float fill = mana.max() > 0 ? mana.value() / (float) mana.max() : 0;
            float overflow = mana.max() > 0 ? mana.overflow() / (float) mana.max() : 0;
            String text = (StatusBarTracker.isManaEstimated() ? "~" : "") + formatPlain(mana.value());
            if (showManaMax) {
                text += "/" + formatPlain(mana.max());
            }
            IconSide iconSide = IconSide.from(ConfigManager.statusBarManaIcon.getValue());
            String overflowText = mana.overflow() > 0 ? " +" + formatPlain(mana.overflow()) : "";
            bars.add(new BarRender(left + 91, top, 91, fill, overflow,
                    MANA_COLOR, MANA_OVERFLOW_COLOR, MANA_TEXT_COLOR, iconSide, MANA_ICON, text,
                    overflowText, MANA_OVERFLOW_TEXT_COLOR));
            addCostPreview(costPreviews, left + 91, top, 91, iconSide,
                    mana.value(), mana.max(), StatusBarTracker.getManaCost());
        }

        // 经验条放在血条/蓝条下方，位置和宽度对齐原版经验条
        bars.add(new BarRender(left, screenHeight - 32, 182, player.experienceProgress, 0,
                EXPERIENCE_COLOR, EXPERIENCE_COLOR, EXPERIENCE_TEXT_COLOR,
                IconSide.from(ConfigManager.statusBarExperienceIcon.getValue()), EXPERIENCE_ICON,
                Integer.toString(player.experienceLevel)));

        // 物品栏左侧从下往上：速度、防御、氧气、Vitality
        int stackWidth = 64;
        int stackX = Math.max(2, screenWidth / 2 - 93 - stackWidth);
        int stackY = screenHeight - 11;

        StatusBarTracker.Resource speed = StatusBarTracker.getSpeed();
        bars.add(new BarRender(stackX, stackY, stackWidth,
                speed.max() > 0 ? speed.value() / (float) speed.max() : 0, 0,
                SPEED_COLOR, SPEED_COLOR, SPEED_TEXT_COLOR,
                IconSide.from(ConfigManager.statusBarSpeedIcon.getValue()), SPEED_ICON, format(speed.value())));
        stackY -= 10;

        if (StatusBarTracker.hasDefenseData()) {
            int defense = StatusBarTracker.getDefense();
            bars.add(new BarRender(stackX, stackY, stackWidth, defense / (defense + 100f), 0,
                    DEFENSE_COLOR, DEFENSE_COLOR, DEFENSE_TEXT_COLOR,
                    IconSide.from(ConfigManager.statusBarDefenseIcon.getValue()), DEFENSE_ICON, format(defense)));
            stackY -= 10;
        }

        if (player.isUnderWater()) {
            StatusBarTracker.Resource air = StatusBarTracker.getAir();
            bars.add(new BarRender(stackX, stackY, stackWidth,
                    air.max() > 0 ? air.value() / (float) air.max() : 0, 0,
                    AIR_COLOR, AIR_COLOR, AIR_TEXT_COLOR,
                    IconSide.from(ConfigManager.statusBarAirIcon.getValue()), AIR_ICON, format(air.value())));
            stackY -= 10;
        }

        if (StatusBarTracker.hasVitalityData()) {
            StatusBarTracker.Resource vitality = StatusBarTracker.getVitality();
            IconSide iconSide = IconSide.from(ConfigManager.statusBarVitalityIcon.getValue());
            bars.add(new BarRender(stackX, stackY, stackWidth,
                    vitality.max() > 0 ? vitality.value() / (float) vitality.max() : 0, 0,
                    VITALITY_COLOR, VITALITY_COLOR, VITALITY_TEXT_COLOR,
                    iconSide, VITALITY_ICON, format(vitality.value())));
            addCostPreview(costPreviews, stackX, stackY, stackWidth, iconSide,
                    vitality.value(), vitality.max(), StatusBarTracker.getVitalityCost());
        }

        // 先画完所有条的背景/填充/图标，再统一画文字，避免文字被其他条盖住
        for (BarRender bar : bars) {
            drawBarBackground(context, bar);
        }
        for (CostPreview preview : costPreviews) {
            drawCostPreview(context, preview);
        }
        for (BarRender bar : bars) {
            drawBarText(context, bar);
        }
    }

    private static void addCostPreview(List<CostPreview> previews, int x, int y, int width, IconSide iconSide,
                                       int value, int max, int cost) {
        if (max <= 0 || cost <= 0) {
            return;
        }
        int visible = Math.min(value, max);
        int applied = Math.min(cost, visible);
        if (applied <= 0) {
            return;
        }
        float start = (visible - applied) / (float) max;
        float fraction = applied / (float) max;
        previews.add(new CostPreview(x, y, width, iconSide, start, fraction));
    }

    private void drawCostPreview(GuiGraphicsExtractor graphics, CostPreview preview) {
        int barX = preview.x();
        int barWidth = preview.width();
        switch (preview.iconSide()) {
            case LEFT -> {
                barX += 10;
                barWidth -= 10;
            }
            case RIGHT -> barWidth -= 10;
            case OFF -> {
            }
        }
        if (barWidth <= 0) {
            return;
        }

        int innerWidth = barWidth - 2;
        if (innerWidth <= 0) {
            return;
        }

        int startOffset = Math.round(innerWidth * clamp01(preview.start()));
        int previewWidth = Math.round(innerWidth * clamp01(preview.fraction()));
        previewWidth = Math.min(previewWidth, innerWidth - startOffset);
        if (previewWidth <= 0) {
            return;
        }

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_FILL,
                barX + 1 + startOffset, preview.y() + 2, previewWidth, 5, costOverlayColor());
    }

    /**
     * 消耗预览颜色：橙色 -> 黑色 -> 橙色，周期 1 秒，固定 alpha 175。
     */
    private static int costOverlayColor() {
        float phase = (float) ((Util.getMillis() % 2000L) / 2000.0);
        float factor = 0.5f + 0.5f * (float) Math.cos(phase * Math.PI * 2);
        int red = Math.round(255 * factor);
        int green = Math.round(165 * factor);
        return ((int) Mth.clampedLerp(factor, 155, 199) << 24) | (red << 16) | (green << 8);
    }

    private void drawBarBackground(GuiGraphicsExtractor graphics, BarRender bar) {
        int barX = bar.x();
        int barWidth = bar.width();
        switch (bar.iconSide()) {
            case LEFT -> {
                barX += 10;
                barWidth -= 10;
            }
            case RIGHT -> barWidth -= 10;
            case OFF -> {
            }
        }
        if (barWidth <= 0) {
            return;
        }

        switch (bar.iconSide()) {
            case LEFT -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED, bar.icon(), bar.x(), bar.y(), 9, 9, 0xFFFFFFFF);
            case RIGHT -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED, bar.icon(), bar.x() + bar.width() - 9, bar.y(), 9, 9, 0xFFFFFFFF);
            case OFF -> {
            }
        }

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_BACK, barX, bar.y() + 1, barWidth, 7, 0xFFFFFFFF);

        int innerWidth = barWidth - 2;
        if (innerWidth > 0) {
            int fillWidth = Math.round(innerWidth * clamp01(bar.fill()));
            if (fillWidth > 0) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_FILL, barX + 1, bar.y() + 2, fillWidth, 5, bar.color());
            }
            int overflowWidth = Math.round(innerWidth * clamp01(bar.overflow()));
            if (overflowWidth > 0) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_FILL, barX + 1, bar.y() + 2, overflowWidth, 5, bar.overflowColor());
            }
        }
    }

    private void drawBarText(GuiGraphicsExtractor graphics, BarRender bar) {
        int barX = bar.x();
        int barWidth = bar.width();
        switch (bar.iconSide()) {
            case LEFT -> {
                barX += 10;
                barWidth -= 10;
            }
            case RIGHT -> barWidth -= 10;
            case OFF -> {
            }
        }
        if (barWidth <= 0) {
            return;
        }

        String text = bar.text();
        String overflowText = bar.overflowText();
        int baseWidth = mc.font.width(text);
        int totalWidth = baseWidth + (overflowText.isEmpty() ? 0 : mc.font.width(overflowText));
        int textX = barX + (barWidth - totalWidth) / 2;
        int textY = bar.y() - 3;

        drawOutlinedText(graphics, text, textX, textY, bar.textColor());
        if (!overflowText.isEmpty()) {
            drawOutlinedText(graphics, overflowText, textX + baseWidth, textY, bar.overflowTextColor());
        }
    }

    private void drawOutlinedText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        int outline = 0xFF000000;
        graphics.text(mc.font, text, x - 1, y, outline, false);
        graphics.text(mc.font, text, x + 1, y, outline, false);
        graphics.text(mc.font, text, x, y - 1, outline, false);
        graphics.text(mc.font, text, x, y + 1, outline, false);
        graphics.text(mc.font, text, x, y, color, false);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static String format(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String formatPlain(int value) {
        return Integer.toString(value);
    }

    private record BarRender(int x, int y, int width, float fill, float overflow,
                             int color, int overflowColor, int textColor,
                             IconSide iconSide, Identifier icon, String text,
                             String overflowText, int overflowTextColor) {

        private BarRender(int x, int y, int width, float fill, float overflow,
                          int color, int overflowColor, int textColor,
                          IconSide iconSide, Identifier icon, String text) {
            this(x, y, width, fill, overflow, color, overflowColor, textColor,
                    iconSide, icon, text, "", 0);
        }
    }

    private record CostPreview(int x, int y, int width, IconSide iconSide, float start, float fraction) {
    }

    private enum IconSide {
        OFF,
        LEFT,
        RIGHT;

        private static IconSide from(int value) {
            return value == 1 ? LEFT : value == 2 ? RIGHT : OFF;
        }
    }

    @Override
    public void renderEffect(GuiGraphicsExtractor context, DeltaTracker tickCounter, boolean force, HudOffsetAndScale settings) {
    }

    @Nullable
    @Override
    public String getHudName() {
        return null;
    }
}
