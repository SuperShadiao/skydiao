package pers.XiaoShadiao.skydiao.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.MaskTimerListener;

import java.util.List;

public final class MaskCard implements IslandCard {

    private static final int PAD = 10;
    private static final int ROW_HEIGHT = 30;
    private static final int ROW_GAP = 6;

    private static final int ICON_SIZE = 20;
    private static final int ICON_GAP = 8;
    private static final int ICON_TEXTURE_SIZE = 128;

    private static final int TEXT_TOP = 4;
    private static final int TIME_TOP = 4;
    private static final int TEXT_GAP = 8;
    private static final int CONTENT_MIN_WIDTH = 88;
    private static final int BAR_EXTRA = 12;

    private static final int BAR_TOP = 19;
    private static final int BAR_HEIGHT = 6;

    private static final int TEXT = 0xFFF2F2F5;
    private static final int MUTED = 0xFFB8BBC5;
    private static final int SUCCESS = 0xFF63D08A;
    private static final int WARNING = 0xFFFFB45C;
    private static final int BAR_TRACK = 0xFF33414A;

    @Override
    public int priority() {
        return 300;
    }

    @Override
    public boolean visible() {
        return ConfigManager.maskTimer.getValue() && !rows().isEmpty();
    }

    @Override
    public int width(Minecraft client) {
        int contentWidth = 0;
        for (MaskTimerListener.Row row : rows()) {
            int textWidth = client.font.width(row.name()) + TEXT_GAP + client.font.width(row.time());
            contentWidth = Math.max(contentWidth, Math.max(textWidth, CONTENT_MIN_WIDTH));
        }
        return PAD * 2 + ICON_SIZE + ICON_GAP + contentWidth + BAR_EXTRA;
    }

    @Override
    public int height() {
        List<MaskTimerListener.Row> rows = rows();
        if (rows.isEmpty()) return 0;
        return PAD * 2 + rows.size() * ROW_HEIGHT + (rows.size() - 1) * ROW_GAP;
    }

    @Override
    public void render(GuiGraphicsExtractor context, int x, int y, int alpha) {
        Minecraft client = Minecraft.getInstance();
        List<MaskTimerListener.Row> rows = rows();
        if (rows.isEmpty()) return;

        int contentWidth = 0;
        for (MaskTimerListener.Row row : rows) {
            int textWidth = client.font.width(row.name()) + TEXT_GAP + client.font.width(row.time());
            contentWidth = Math.max(contentWidth, Math.max(textWidth, CONTENT_MIN_WIDTH));
        }
        int barWidth = contentWidth + BAR_EXTRA;

        int rowY = y + PAD;
        for (MaskTimerListener.Row row : rows) {
            int timeWidth = client.font.width(row.time());
            int textWidth = Math.max(client.font.width(row.name()) + TEXT_GAP + timeWidth, CONTENT_MIN_WIDTH);
            int contentX = x + PAD;
            int textX = contentX + ICON_SIZE + ICON_GAP;

            context.blit(RenderPipelines.GUI_TEXTURED, row.icon(), contentX, rowY + (ROW_HEIGHT - ICON_SIZE) / 2,
                    0, 0, ICON_SIZE, ICON_SIZE,
                    ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE,
                    withAlpha(alpha, 0xFFFFFF));
            HudDraw.text(context, client.font, row.name(), textX, rowY + TEXT_TOP, 1F, withAlpha(alpha, TEXT));
            HudDraw.text(context, client.font, row.time(), textX + textWidth - timeWidth, rowY + TIME_TOP, 1F,
                    withAlpha(alpha, row.active() ? SUCCESS : MUTED));

            int barY = rowY + BAR_TOP;
            HudDraw.bar(context, textX, barY, barWidth, BAR_HEIGHT, withAlpha(alpha, BAR_TRACK));
            HudDraw.bar(context, textX, barY, Math.round(barWidth * row.cooldownFraction()), BAR_HEIGHT,
                    withAlpha(alpha, WARNING));
            if (row.active()) {
                HudDraw.bar(context, textX, barY, Math.round(barWidth * row.invincibilityFraction()), BAR_HEIGHT,
                        withAlpha(alpha, SUCCESS));
            }
            rowY += ROW_HEIGHT + ROW_GAP;
        }
    }

    private static List<MaskTimerListener.Row> rows() {
        return MaskTimerListener.rows();
    }

    private static int withAlpha(int alpha, int rgb) {
        return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }
}
