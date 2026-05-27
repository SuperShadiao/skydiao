package pers.XiaoShadiao.skydiao.utils;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.impl.client.rendering.world.WorldRenderContextImpl;
import net.minecraft.client.gui.GuiGraphics;

public class ClientRenderCrashFixer {

    public static WorldRenderContextImpl wrc;
    public static GuiGraphics gg;

    public static void fix() {
        if (wrc != null) {
            if(wrc.matrices() != null) {
                while(!wrc.matrices().isEmpty()) wrc.matrices().popPose();
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
