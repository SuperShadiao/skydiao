package pers.XiaoShadiao.skydiao.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface IslandCard {

    int priority();

    boolean visible();

    int width(Minecraft client);

    int height();

    void render(GuiGraphicsExtractor context, int x, int y, int alpha);
}
