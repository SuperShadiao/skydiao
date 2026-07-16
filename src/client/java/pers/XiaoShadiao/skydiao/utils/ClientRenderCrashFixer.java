package pers.XiaoShadiao.skydiao.utils;

import net.fabricmc.fabric.impl.client.rendering.level.LevelRenderContextImpl;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class ClientRenderCrashFixer {

    public static LevelRenderContextImpl wrc;
    public static GuiGraphicsExtractor gg;

    public static void fix() {
        if (wrc != null) {
            if(wrc.poseStack() != null) {
                while(!wrc.poseStack().isEmpty()) wrc.poseStack().popPose();
            }
        }
        if (gg != null) {
            while (true) {
                try {
                    gg.pose().popMatrix();
                } catch (Throwable e) {
                    break;
                }
            }
        }
    }
}
