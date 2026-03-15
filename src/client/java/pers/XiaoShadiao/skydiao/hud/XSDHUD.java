package pers.XiaoShadiao.skydiao.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import pers.XiaoShadiao.skydiao.utils.Register;

public abstract class XSDHUD implements HudElement {

    public static final Minecraft mc = Minecraft.getInstance();
    public static final CustomBossbar customBossbar = new CustomBossbar();
    public static final StarRailNotification starRailNotification = new StarRailNotification();
    public static final BlindOrDying blindOrDying = new BlindOrDying();
    public static void init() {
        Register.execRegister(XSDHUD.class, XSDHUD.class, XSDHUD::runRegister);
    }

    public abstract void runRegister();

}
