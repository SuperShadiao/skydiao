package pers.XiaoShadiao.skydiao.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AlertListener;

import java.util.List;

public final class AlertCard implements IslandCard {

    private static final int PAD = 10;
    private static final int ROW_GAP = 6;
    private static final int ROW_HEIGHT = 30;

    private static final int ICON_SIZE = 20;
    private static final int ICON_GAP = 8;
    private static final int ICON_TEXTURE_SIZE = 128;

    private static final int TITLE_TOP = 4;
    private static final int SUBTITLE_TOP = 16;
    private static final int CONTENT_MIN_WIDTH = 80;

    private static final int TEXT = 0xFFF2F2F5;
    private static final int MUTED = 0xFFB8BBC5;

    private static final Identifier WARNING_ICON =
            Identifier.fromNamespaceAndPath("skydiao", "textures/gui/dynamic_spot/alert_warning.png");
    private static final Identifier SUCCESS_ICON =
            Identifier.fromNamespaceAndPath("skydiao", "textures/gui/dynamic_spot/alert_success.png");

    @Override
    public int priority() {
        return 400;
    }

    @Override
    public boolean visible() {
        return ConfigManager.alertHud.getValue() && !rows().isEmpty();
    }

    @Override
    public int width(Minecraft client) {
        int contentWidth = 0;
        for (AlertListener.Row row : rows()) {
            int textWidth = Math.max(client.font.width(row.title()), client.font.width(row.subtitle()));
            contentWidth = Math.max(contentWidth, ICON_SIZE + ICON_GAP + Math.max(textWidth, CONTENT_MIN_WIDTH));
        }
        return PAD * 2 + contentWidth;
    }

    @Override
    public int height() {
        List<AlertListener.Row> rows = rows();
        if (rows.isEmpty()) return 0;
        int height = PAD * 2;
        int gaps = 0;
        for (int i = 0; i < rows.size(); i++) {
            height += Math.round(ROW_HEIGHT * rows.get(i).reveal());
            if (i > 0) gaps += Math.round(ROW_GAP * rows.get(i).reveal());
        }
        return height + gaps;
    }

    @Override
    public void render(GuiGraphicsExtractor context, int x, int y, int alpha) {
        Minecraft client = Minecraft.getInstance();
        List<AlertListener.Row> rows = rows();
        int rowY = y + PAD;
        int innerBottom = y + height() - PAD;
        for (int i = 0; i < rows.size(); i++) {
            AlertListener.Row row = rows.get(i);
            int rowHeight = Math.round(ROW_HEIGHT * row.reveal());
            rowY += i == 0 ? 0 : Math.round(ROW_GAP * row.reveal());

            int contentX = x + PAD;
            int textX = contentX + ICON_SIZE + ICON_GAP;
            int bandBottom = Math.min(rowY + rowHeight, innerBottom);

            int iconWidth = Math.round(ICON_SIZE * row.reveal());
            if (iconWidth > 0 && bandBottom > rowY) {
                context.enableScissor(contentX, rowY, contentX + iconWidth, bandBottom);
                context.blit(RenderPipelines.GUI_TEXTURED, row.icon(), contentX,
                        rowY + (ROW_HEIGHT - ICON_SIZE) / 2, 0, 0, ICON_SIZE, ICON_SIZE,
                        ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE,
                        withAlpha(alpha, 0xFFFFFF));
                context.disableScissor();
            }
            if (rowHeight > 1) {
                int textWidth = Math.max(client.font.width(row.title()), client.font.width(row.subtitle()));
                context.enableScissor(textX, rowY, textX + Math.max(textWidth, CONTENT_MIN_WIDTH), bandBottom);
                context.text(client.font, net.minecraft.network.chat.Component.literal(row.title()), textX,
                        rowY + TITLE_TOP, withAlpha(alpha, TEXT));
                context.text(client.font, net.minecraft.network.chat.Component.literal(row.subtitle()), textX,
                        rowY + SUBTITLE_TOP, withAlpha(alpha, MUTED));
                context.disableScissor();
            }
            rowY += rowHeight;
        }
    }

    private static List<AlertListener.Row> rows() {
        return AlertListener.rows();
    }

    private static int withAlpha(int alpha, int rgb) {
        return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }
}
