package pers.XiaoShadiao.skydiao.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class HudDraw {

    private HudDraw() {
    }

    public static void text(GuiGraphicsExtractor context, Font font, String text, int x, int y,
                            float scale, int color) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        context.text(font, Component.literal(text), 0, 0, color);
        pose.popMatrix();
    }

    public static int textWidth(Font font, String text, float scale) {
        return Math.round(font.width(text) * scale);
    }

    public static void bar(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        int radius = height / 2;
        for (int row = 0; row < height; row++) {
            int inset = inset(row, height, radius);
            int x1 = x + inset;
            int x2 = x + width - inset;
            if (x2 <= x1) continue;
            context.fill(x1, y + row, x2, y + row + 1, color);
        }
    }

    private static int inset(int row, int height, int radius) {
        if (radius <= 0) return 0;
        float center = (height - 1) / 2F;
        float dy = row - center;
        float squared = 1F - (dy * dy) / (radius * radius);
        if (squared <= 0F) return radius;
        return Math.round(radius - radius * (float) Math.sqrt(squared));
    }
}
