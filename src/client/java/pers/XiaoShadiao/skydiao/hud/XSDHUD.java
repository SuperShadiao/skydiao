package pers.XiaoShadiao.skydiao.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.slayer.vs.AutoBloodfiendListener;
import pers.XiaoShadiao.skydiao.utils.Register;

public abstract class XSDHUD implements HudElement {

    public static final Minecraft mc = Minecraft.getInstance();

    public static final CustomBossbar customBossbar = new CustomBossbar();
    public static final StarRailNotification starRailNotification = new StarRailNotification();
    public static final BlindOrDying blindOrDying = new BlindOrDying();
    public static final BigTitle bigTitle = new BigTitle();
    public static final HUDCrashFixer hudCrashFixer = new HUDCrashFixer();
    public static final AutoBloodfiendListener.TaskRender abfTaskRender = MacroManagerListener.autoBloodfiendListener.new TaskRender();
    public static final GenshinImpactHeatCold genshinImpactHeatCold = new GenshinImpactHeatCold();

    public static void init() {
        Register.execRegister(XSDHUD.class, XSDHUD.class, XSDHUD::runRegister);
    }

    public abstract void runRegister();

    public abstract void render(GuiGraphicsExtractor context, DeltaTracker tickCounter);

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        render(graphics, deltaTracker);
    }
}
