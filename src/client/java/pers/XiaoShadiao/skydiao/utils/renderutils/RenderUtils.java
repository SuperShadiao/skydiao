package pers.XiaoShadiao.skydiao.utils.renderutils;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class RenderUtils {

    public static void renderScrollingString(
            GuiGraphics guiGraphics,
            Font font,
            Component text,
            int centerX,      // 基准 X，用于居中绘制时的中心点
            int left,         // 绘制区域左边界
            int top,          // 绘制区域上边界
            int right,        // 绘制区域右边界
            int bottom,       // 绘制区域下边界
            int color         // 文字颜色 (ARGB)
    ) {
        int textWidth = font.width(text);
        int textY = (top + bottom - 9) / 2 + 1; // 在上下边界之间居中
        int availableWidth = right - left;

        if (textWidth > availableWidth) {
            int overflow = textWidth - availableWidth;
            double time = Util.getMillis() / 500.0;
            double period = Math.max(overflow * 0.5, 3.0);
            double oscillation = Math.sin((Math.PI / 2) * Math.cos((Math.PI * 2) * time / period)) / 2.0 + 0.5;
            double offset = Mth.lerp(oscillation, 0.0, overflow);

            guiGraphics.enableScissor(left, top, right, bottom);
            guiGraphics.drawString(font, text, left - (int) offset, textY, color);
            guiGraphics.disableScissor();
        } else {
            int drawX = Mth.clamp(centerX, left + textWidth / 2, right - textWidth / 2);
            guiGraphics.drawCenteredString(font, text, drawX, textY, color);
        }
    }


}
